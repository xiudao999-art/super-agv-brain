-- 将已部署的动态 Action 1.0 表结构迁移到 Action 2.0。
--
-- 执行要求：
-- 1. 本脚本只能执行一次；必须先备份五张 Action 表，并在同结构测试库完成演练。
-- 2. 执行期间停止 Action 服务和配置写入，且不得存在未结束的 Action 执行实例。
-- 3. 源库必须已具备 action_definition、action_parameter_set、
--    action_error_mapping_rule、action_execution、action_execution_event 五张表，
--    且尚未增加 Action 2.0 字段。
-- 4. MySQL DDL 会隐式提交；任一 ALTER 失败后不得直接重跑，必须从备份恢复后排查。
-- 5. 本脚本只迁移数据库结构和历史证据，不提供 1.0 运行时协议兼容。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 上一次若被前置校验拦截，过程可能仍然存在；清理过程本身不改变业务表。
drop procedure if exists assert_action_v2_migration_ready;

delimiter $$

create procedure assert_action_v2_migration_ready()
begin
    declare source_table_count integer default 0;
    declare legacy_column_count integer default 0;
    declare target_column_count integer default 0;
    declare legacy_index_count integer default 0;
    declare legacy_foreign_key_count integer default 0;
    declare invalid_definition_count integer default 0;
    declare active_execution_count integer default 0;

    if database() is null then
        signal sqlstate '45000'
            set message_text = '未选择目标数据库，拒绝执行 Action 2.0 迁移';
    end if;

    select count(*)
      into source_table_count
      from information_schema.tables
     where table_schema = database()
       and table_name in (
           'action_definition',
           'action_parameter_set',
           'action_error_mapping_rule',
           'action_execution',
           'action_execution_event'
       );

    if source_table_count <> 5 then
        signal sqlstate '45000'
            set message_text = 'Action 源表不完整，拒绝执行 Action 2.0 迁移';
    end if;

    select count(*)
      into legacy_column_count
      from information_schema.columns
     where table_schema = database()
       and (
           (table_name = 'action_definition' and column_name = 'downstream_action_type')
           or (table_name = 'action_execution' and column_name in (
               'downstream_action_type',
               'protocol_action_version',
               'physical_result_known',
               'physical_result_json'
           ))
       );

    select count(*)
      into target_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_execution'
       and column_name in (
           'protocol_version',
           'physical_outcome',
           'error_mapping_snapshot_json',
           'last_step_event_json'
       );

    if legacy_column_count <> 5 or target_column_count <> 0 then
        signal sqlstate '45000'
            set message_text = 'Action 数据库不是预期的 1.0 源结构，可能已迁移或处于不完整状态';
    end if;

    select count(distinct concat(table_name, ':', index_name))
      into legacy_index_count
      from information_schema.statistics
     where table_schema = database()
       and (
           (table_name = 'action_definition' and index_name = 'ix_action_definition_status')
           or (table_name = 'action_parameter_set' and index_name = 'ix_action_parameter_set_robot')
           or (table_name = 'action_error_mapping_rule' and index_name in (
               'ix_action_error_mapping_status_priority',
               'ix_action_error_mapping_profile'
           ))
           or (table_name = 'action_execution' and index_name = 'ix_action_execution_workflow')
           or (table_name = 'action_execution_event' and index_name = 'ix_action_execution_event_order')
       );

    select count(*)
      into legacy_foreign_key_count
      from information_schema.referential_constraints
     where constraint_schema = database()
       and table_name = 'action_execution_event'
       and constraint_name = 'fk_action_execution_event';

    if legacy_index_count <> 6 or legacy_foreign_key_count <> 1 then
        signal sqlstate '45000'
            set message_text = 'Action 1.0 索引或外键结构不完整，拒绝执行可能产生半迁移的 ALTER';
    end if;

    select count(*)
      into invalid_definition_count
      from action_definition
     where not json_valid(definition_json);

    if invalid_definition_count > 0 then
        signal sqlstate '45000'
            set message_text = '存在无法解析的 Action 定义 JSON，请先修复或归档';
    end if;

    select count(*)
      into active_execution_count
      from action_execution
     where state not in ('PHYSICAL_DONE', 'REJECTED', 'FAILED', 'UNKNOWN_HOLD', 'CANCELLED');

    if active_execution_count > 0 then
        signal sqlstate '45000'
            set message_text = '存在未结束的 Action 执行实例，拒绝切换 2.0';
    end if;
end$$

delimiter ;

call assert_action_v2_migration_ready();
drop procedure assert_action_v2_migration_ready;

-- 1. 旧定义 JSON 与 2.0 不同构，保留原始配置用于人工参考，但禁止继续执行。
--    后续必须通过 Action 工作台覆盖为经过校验的 2.0 定义后再启用。
update action_definition
   set status = 'DISABLED',
       revision = revision + 1,
       updated_at = current_timestamp(6)
 where coalesce(json_unquote(json_extract(definition_json, '$.schemaVersion')), '') <> '2.0';

alter table action_definition
    drop index ix_action_definition_status,
    drop column downstream_action_type;

-- 删除不再属于 2.0 基线的冗余索引；业务数据保持不变。
alter table action_parameter_set
    drop index ix_action_parameter_set_robot;

alter table action_error_mapping_rule
    drop index ix_action_error_mapping_status_priority,
    drop index ix_action_error_mapping_profile,
    add index ix_action_error_mapping_active (status, priority, rule_id);

-- 2. 先以可空列承接历史数据，再回填并收紧非空约束，避免伪造 2.0 执行事实。
alter table action_execution
    add column physical_outcome varchar(32) null after state,
    add column error_mapping_snapshot_json longtext null after command_input_json,
    add column last_step_event_json longtext null after error_mapping_snapshot_json;

update action_execution
   set physical_outcome = case
           when state = 'PHYSICAL_DONE' then 'CONFIRMED_SUCCEEDED'
           when state in ('DISPATCH_PENDING', 'REJECTED') then 'NOT_STARTED'
           when state = 'FAILED' and physical_result_known = true then 'CONFIRMED_FAILED'
           else 'UNKNOWN'
       end,
       error_mapping_snapshot_json = '{"schemaVersion":"2.0","configHash":"aa51d765e937d37bd5c21d1cad4270aab6db194fdf9a299bbe5c40f06bd46a02","rules":[],"fallback":{"businessCode":"5999","businessMessage":"未映射设备异常","reasonCode":"DEVICE.UNMAPPED_FAULT","handlingConstraint":"MANUAL_INTERVENTION","handlingAdvice":"保留厂家原始异常并补充映射规则"}}';

-- 2.0 将成功终态统一命名为 FINISHED；旧的协议版本值原样保留为历史证据。
update action_execution
   set state = 'FINISHED'
 where state = 'PHYSICAL_DONE';

alter table action_execution
    change column protocol_action_version protocol_version varchar(16) not null,
    modify column physical_outcome varchar(32) not null,
    modify column error_mapping_snapshot_json longtext not null,
    drop column downstream_action_type,
    drop column physical_result_known,
    drop column physical_result_json,
    drop index ix_action_execution_workflow;

-- 3. 事件载荷继续原样保留，只将索引和约束名称、列顺序对齐 2.0 基线。
alter table action_execution_event
    drop foreign key fk_action_execution_event,
    drop index ix_action_execution_event_order,
    add constraint fk_action_event_execution foreign key (action_instance_id)
        references action_execution (action_instance_id),
    add index ix_action_event_execution (action_instance_id, received_at, event_sequence);

-- 执行后必须核对：
-- 1. action_execution 中不存在 PHYSICAL_DONE，且 physical_outcome/error_mapping_snapshot_json 均非空；
-- 2. 所有旧版定义均为 DISABLED；
-- 3. 应用以 spring.jpa.hibernate.ddl-auto=validate 启动通过后，再重新录入并启用 2.0 定义。

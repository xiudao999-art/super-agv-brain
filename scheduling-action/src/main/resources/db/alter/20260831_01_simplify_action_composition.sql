-- 从当前已部署的动态 Action 旧结构直接切换到子动作编排新结构。
--
-- 本次不迁移旧定义、参数集和执行记录，而是重建四张相关表；异常映射规则保留。
-- 执行要求：
-- 1. 停止 Action 服务及所有可能写入 Action 表的进程；
-- 2. 确认没有活动 Action，并完成目标库备份；
-- 3. 本脚本只能对前置检查通过的旧结构执行一次；
-- 4. MySQL DDL 会隐式提交，任何语句失败后必须先核对实际结构，禁止直接重跑。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

drop procedure if exists assert_action_composition_cutover_ready;

delimiter $$

create procedure assert_action_composition_cutover_ready()
begin
    declare source_table_count integer default 0;
    declare legacy_column_count integer default 0;
    declare target_column_count integer default 0;
    declare legacy_index_count integer default 0;
    declare active_execution_count integer default 0;

    if database() is null then
        signal sqlstate '45000'
            set message_text = '未选择目标数据库，拒绝执行 Action 子动作编排切换';
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
            set message_text = 'Action 旧表不完整，拒绝执行重建';
    end if;

    select count(*)
      into legacy_column_count
      from information_schema.columns
     where table_schema = database()
       and (
           (table_name = 'action_definition' and column_name in (
               'action_key', 'downstream_action_type', 'revision', 'status'
           ))
           or (table_name = 'action_execution' and column_name in (
               'action_key', 'action_revision', 'downstream_action_type',
               'parameter_set_id', 'parameter_set_revision',
               'protocol_action_version', 'workflow_instance_id',
               'workflow_node_instance_id', 'physical_result_known',
               'definition_snapshot_json', 'parameter_snapshot_json',
               'physical_result_json'
           ))
       );

    select count(*)
      into target_column_count
      from information_schema.columns
     where table_schema = database()
       and (
           (table_name = 'action_definition' and column_name in ('name', 'enabled'))
           or (table_name = 'action_execution' and column_name in (
               'action_definition_id', 'protocol_version', 'physical_outcome',
               'last_step_event_json'
           ))
       );

    if legacy_column_count <> 16 or target_column_count <> 0 then
        signal sqlstate '45000'
            set message_text = 'Action 数据库不是预期旧结构，可能已迁移或处于不完整状态';
    end if;

    select count(distinct concat(table_name, ':', index_name))
      into legacy_index_count
      from information_schema.statistics
     where table_schema = database()
       and (
           (table_name = 'action_definition' and index_name = 'uk_action_definition_key')
           or (table_name = 'action_parameter_set' and index_name = 'ix_action_parameter_set_robot')
           or (table_name = 'action_error_mapping_rule' and index_name in (
               'ix_action_error_mapping_status_priority',
               'ix_action_error_mapping_profile'
           ))
           or (table_name = 'action_execution' and index_name in (
               'ix_action_execution_action_state',
               'ix_action_execution_parameter_state'
           ))
           or (table_name = 'action_execution_event' and index_name = 'ix_action_execution_event_order')
       );

    if legacy_index_count <> 7 then
        signal sqlstate '45000'
            set message_text = 'Action 旧索引结构与预期不一致，拒绝执行可能产生半迁移的重建';
    end if;

    select count(*)
      into active_execution_count
      from action_execution
     where state not in ('PHYSICAL_DONE', 'REJECTED', 'FAILED', 'UNKNOWN_HOLD', 'CANCELLED');

    if active_execution_count > 0 then
        signal sqlstate '45000'
            set message_text = '存在未结束的 Action 执行实例，拒绝切换结构';
    end if;
end$$

delimiter ;

call assert_action_composition_cutover_ready();
drop procedure assert_action_composition_cutover_ready;

-- 旧配置与新结构不同构，且本期明确允许清空开发执行数据，因此直接重建以消除残留列。
drop table action_execution_event;
drop table action_execution;
drop table action_parameter_set;
drop table action_definition;

create table action_definition (
    id varchar(36) primary key,
    name varchar(128) not null,
    enabled boolean not null,
    definition_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    index ix_action_definition_name (name, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution (
    action_instance_id varchar(128) primary key,
    action_definition_id varchar(36) not null,
    robot_id varchar(128) not null,
    device_command_id varchar(128) not null,
    protocol_version varchar(16) not null,
    request_hash varchar(64) not null,
    package_hash varchar(64) not null,
    state varchar(32) not null,
    physical_outcome varchar(32) not null,
    timeout_ms integer not null,
    command_input_json longtext not null,
    last_step_event_json longtext,
    resolved_steps_json longtext,
    error_json longtext,
    dispatch_session_id varchar(64),
    dispatch_message_id varchar(64),
    last_event_message_id varchar(64),
    last_event_session_id varchar(64),
    last_event_sequence bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    row_version bigint not null default 0,
    constraint uk_action_execution_device_command unique (device_command_id),
    index ix_action_execution_robot_state (robot_id, state, updated_at),
    index ix_action_execution_definition_state (action_definition_id, state, created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution_event (
    message_id varchar(64) primary key,
    action_instance_id varchar(128) not null,
    robot_id varchar(128) not null,
    message_type varchar(32) not null,
    event_sequence bigint not null,
    event_state varchar(32) not null,
    payload_json longtext not null,
    received_at timestamp(6) not null,
    constraint fk_action_event_execution foreign key (action_instance_id)
        references action_execution (action_instance_id),
    index ix_action_event_execution (action_instance_id, received_at, event_sequence)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 异常映射规则本身保留，只将索引收敛到当前查询方式。
alter table action_error_mapping_rule
    drop index ix_action_error_mapping_status_priority,
    drop index ix_action_error_mapping_profile,
    add index ix_action_error_mapping_active (status, priority, rule_id);

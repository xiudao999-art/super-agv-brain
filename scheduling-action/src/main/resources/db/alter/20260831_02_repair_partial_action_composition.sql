-- 修复 20260831_01 在旧版脚本执行期间中断后留下的半迁移结构。
--
-- 适用状态：
-- 1. action_parameter_set 已被删除；
-- 2. action_definition 已切换为 name/enabled，但仍残留 downstream_action_type；
-- 3. action_execution 和 action_execution_event 仍是旧结构；
-- 4. 定义、执行和事件数据均已被前一次脚本清空。
--
-- 本脚本不处理其他状态，也不修改 flow_action 或 robot_action_execution。
-- 执行前必须停止所有 Action 写入、完成数据库备份；DDL 失败后禁止直接重跑。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 上一次前置检查失败时可能遗留过程；删除过程不改变业务表。
drop procedure if exists assert_action_composition_cutover_ready;
drop procedure if exists assert_action_partial_repair_ready;

delimiter $$

create procedure assert_action_partial_repair_ready()
begin
    declare existing_table_count integer default 0;
    declare removed_parameter_table_count integer default 0;
    declare definition_column_count integer default 0;
    declare definition_expected_column_count integer default 0;
    declare legacy_execution_column_count integer default 0;
    declare target_execution_column_count integer default 0;
    declare expected_index_count integer default 0;
    declare disposable_row_count bigint default 0;

    if database() is null then
        signal sqlstate '45000'
            set message_text = '未选择目标数据库，拒绝修复 Action 半迁移结构';
    end if;

    select count(*)
      into existing_table_count
      from information_schema.tables
     where table_schema = database()
       and table_name in (
           'action_definition',
           'action_error_mapping_rule',
           'action_execution',
           'action_execution_event'
       );

    select count(*)
      into removed_parameter_table_count
      from information_schema.tables
     where table_schema = database()
       and table_name = 'action_parameter_set';

    if existing_table_count <> 4 or removed_parameter_table_count <> 0 then
        signal sqlstate '45000'
            set message_text = 'Action 表数量不符合已知半迁移状态，拒绝修复';
    end if;

    select count(*)
      into definition_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_definition';

    select count(*)
      into definition_expected_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_definition'
       and column_name in (
           'id', 'name', 'enabled', 'downstream_action_type',
           'definition_json', 'created_at', 'updated_at'
       );

    if definition_column_count <> 7 or definition_expected_column_count <> 7 then
        signal sqlstate '45000'
            set message_text = 'action_definition 不符合已知半迁移结构，拒绝修复';
    end if;

    select count(*)
      into legacy_execution_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_execution'
       and column_name in (
           'action_key', 'action_revision', 'downstream_action_type',
           'parameter_set_id', 'parameter_set_revision',
           'protocol_action_version', 'workflow_instance_id',
           'workflow_node_instance_id', 'physical_result_known',
           'definition_snapshot_json', 'parameter_snapshot_json',
           'physical_result_json'
       );

    select count(*)
      into target_execution_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_execution'
       and column_name in (
           'action_definition_id', 'protocol_version',
           'physical_outcome', 'last_step_event_json'
       );

    if legacy_execution_column_count <> 12 or target_execution_column_count <> 0 then
        signal sqlstate '45000'
            set message_text = 'action_execution 不符合已知半迁移结构，拒绝修复';
    end if;

    select count(distinct concat(table_name, ':', index_name))
      into expected_index_count
      from information_schema.statistics
     where table_schema = database()
       and (
           (table_name = 'action_definition' and index_name = 'ix_action_definition_name')
           or (table_name = 'action_error_mapping_rule' and index_name in (
               'ix_action_error_mapping_status_priority',
               'ix_action_error_mapping_profile'
           ))
           or (table_name = 'action_execution' and index_name in (
               'ix_action_execution_action_state',
               'ix_action_execution_parameter_state',
               'ix_action_execution_robot_state',
               'ix_action_execution_workflow'
           ))
           or (table_name = 'action_execution_event'
               and index_name = 'ix_action_execution_event_order')
       );

    if expected_index_count <> 8 then
        signal sqlstate '45000'
            set message_text = 'Action 索引不符合已知半迁移结构，拒绝修复';
    end if;

    select (select count(*) from action_definition)
         + (select count(*) from action_execution)
         + (select count(*) from action_execution_event)
      into disposable_row_count;

    if disposable_row_count <> 0 then
        signal sqlstate '45000'
            set message_text = '半迁移表已重新产生数据，拒绝执行破坏性修复';
    end if;
end$$

delimiter ;

call assert_action_partial_repair_ready();
drop procedure assert_action_partial_repair_ready;

-- 数据已由前一次中断迁移清空，按依赖顺序重建为当前唯一结构。
drop table action_execution_event;
drop table action_execution;
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

-- 异常映射规则表不重建、不清空，只更新索引。
alter table action_error_mapping_rule
    drop index ix_action_error_mapping_status_priority,
    drop index ix_action_error_mapping_profile,
    add index ix_action_error_mapping_active (status, priority, rule_id);

-- 从“版本化编译 + 逐原子执行 / 固定包”迁移到“当前 Action + 动态整包执行”。
-- 执行前必须备份。本脚本为一次性人工迁移，不得重复执行。
-- 本迁移会直接删除旧 Action 表及其中数据；备份是唯一恢复来源。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 先删除带外键的明细表，再删除对应主表，避免依赖约束阻止迁移。
drop table action_execution_node;
drop table action_execution;
drop table robot_action_event;
drop table robot_action_execution;
drop table action_draft;
drop table action_release;
drop table atomic_capability;

create table action_definition (
    id varchar(36) primary key, action_key varchar(128) not null,
    downstream_action_type varchar(64) not null, revision bigint not null,
    status varchar(32) not null, definition_json longtext not null,
    created_at timestamp(6) not null, updated_at timestamp(6) not null,
    constraint uk_action_definition_key unique (action_key),
    index ix_action_definition_status (status, action_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_parameter_set (
    id varchar(36) primary key, action_key varchar(128) not null, name varchar(128) not null,
    robot_id varchar(128), fixture_key varchar(128), material_key varchar(128),
    values_json longtext not null, revision bigint not null, enabled boolean not null,
    created_at timestamp(6) not null, updated_at timestamp(6) not null,
    index ix_action_parameter_set_action (action_key, enabled),
    index ix_action_parameter_set_robot (robot_id, action_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution (
    action_instance_id varchar(128) primary key, robot_id varchar(128) not null,
    device_command_id varchar(128) not null, action_key varchar(128) not null,
    action_revision bigint not null, downstream_action_type varchar(64) not null,
    parameter_set_id varchar(36), parameter_set_revision bigint,
    protocol_action_version varchar(16) not null, request_hash varchar(64) not null,
    package_hash varchar(64) not null, workflow_instance_id varchar(128),
    workflow_node_instance_id varchar(128), state varchar(32) not null,
    physical_result_known boolean not null, timeout_ms integer not null,
    definition_snapshot_json longtext not null, parameter_snapshot_json longtext not null,
    input_snapshot_json longtext not null, command_input_json longtext not null,
    resolved_steps_json longtext, physical_result_json longtext, error_json longtext,
    dispatch_session_id varchar(64), dispatch_message_id varchar(64),
    last_event_message_id varchar(64), last_event_session_id varchar(64), last_event_sequence bigint,
    created_at timestamp(6) not null, updated_at timestamp(6) not null,
    completed_at timestamp(6), row_version bigint not null default 0,
    constraint uk_action_execution_device_command unique (device_command_id),
    index ix_action_execution_action_state (action_key, state, created_at),
    index ix_action_execution_parameter_state (parameter_set_id, state, created_at),
    index ix_action_execution_robot_state (robot_id, state, updated_at),
    index ix_action_execution_workflow (workflow_instance_id, workflow_node_instance_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution_event (
    message_id varchar(64) primary key, action_instance_id varchar(128) not null,
    robot_id varchar(128) not null, message_type varchar(32) not null,
    event_sequence bigint not null, event_state varchar(32) not null,
    payload_json longtext not null, received_at timestamp(6) not null,
    constraint fk_action_execution_event foreign key (action_instance_id)
        references action_execution (action_instance_id),
    index ix_action_execution_event_order (action_instance_id, event_sequence)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 旧 definition_json 与新协议不同构，不做危险的自动转换。
-- 为七种已注册主动作建立空步骤草稿，后续必须重新配置并完成实机联调。
start transaction;
insert into action_definition
    (id, action_key, downstream_action_type, revision, status, definition_json, created_at, updated_at)
values
('20000000-0000-4000-8000-000000000001','MOVE','MOVE',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"MOVE","downstreamActionType":"MOVE","displayName":"底盘移动","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":60000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000002','ARM.PICK','ARM.PICK',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"ARM.PICK","downstreamActionType":"ARM.PICK","displayName":"机械臂抓取","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":90000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000003','ARM.PLACE','ARM.PLACE',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"ARM.PLACE","downstreamActionType":"ARM.PLACE","displayName":"机械臂放置","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":90000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000004','ARM.PICK_BATCH','ARM.PICK_BATCH',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"ARM.PICK_BATCH","downstreamActionType":"ARM.PICK_BATCH","displayName":"批量抓取","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":240000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000005','ARM.PLACE_BATCH','ARM.PLACE_BATCH',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"ARM.PLACE_BATCH","downstreamActionType":"ARM.PLACE_BATCH","displayName":"批量放置","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":240000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000006','ARM.HOME','ARM.HOME',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"ARM.HOME","downstreamActionType":"ARM.HOME","displayName":"机械臂回零","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":60000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('20000000-0000-4000-8000-000000000007','VISION.CAPTURE','VISION.CAPTURE',1,'DRAFT','{"schemaVersion":"1.0","actionKey":"VISION.CAPTURE","downstreamActionType":"VISION.CAPTURE","displayName":"视觉拍照","description":"请重新配置并完成实机联调","inputSchema":{},"parameterSchema":{},"phases":[],"timeoutMs":30000}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
commit;

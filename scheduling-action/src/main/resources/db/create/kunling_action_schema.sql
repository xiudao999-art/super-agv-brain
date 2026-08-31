-- Action 当前全新数据库基线。
-- 本脚本只用于空数据库；执行前必须选择目标数据库并完成备份。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

create table action_definition (
    id varchar(36) primary key,
    name varchar(128) not null,
    enabled boolean not null,
    definition_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    index ix_action_definition_name (name, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_error_mapping_rule (
    rule_id varchar(128) primary key,
    profile_id varchar(128) not null,
    priority integer not null,
    revision bigint not null,
    status varchar(32) not null,
    rule_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    index ix_action_error_mapping_active (status, priority, rule_id)
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

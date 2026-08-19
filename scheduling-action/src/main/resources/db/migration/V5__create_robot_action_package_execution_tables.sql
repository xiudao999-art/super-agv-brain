-- 一期固定 Action 以一个完整动作包下发；先持久化命令快照，再写入机器人 TCP 会话。
create table robot_action_execution (
    action_instance_id varchar(128) primary key,
    robot_id varchar(128) not null,
    device_command_id varchar(128) not null,
    action_type varchar(64) not null,
    action_version varchar(32) not null,
    template_version varchar(32) not null,
    request_hash varchar(64) not null,
    package_hash varchar(64) not null,
    workflow_instance_id varchar(128),
    workflow_node_instance_id varchar(128),
    state varchar(32) not null,
    physical_result_known boolean not null,
    timeout_ms integer not null,
    request_input_json longtext not null,
    command_input_json longtext not null,
    resolved_steps_json longtext,
    physical_result_json longtext,
    error_json longtext,
    dispatch_session_id varchar(64),
    dispatch_message_id varchar(64),
    last_event_message_id varchar(64),
    last_event_sequence bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    row_version bigint not null default 0,
    constraint uk_robot_action_device_command unique (device_command_id)
);

create index ix_robot_action_robot_state
    on robot_action_execution (robot_id, state, updated_at);
create index ix_robot_action_workflow
    on robot_action_execution (workflow_instance_id, workflow_node_instance_id);

-- 原始事件独立保存，message_id 去重；便于一期联调审计和后续人工处置取证。
create table robot_action_event (
    message_id varchar(64) primary key,
    action_instance_id varchar(128) not null,
    robot_id varchar(128) not null,
    message_type varchar(32) not null,
    event_sequence bigint not null,
    event_state varchar(32) not null,
    payload_json longtext not null,
    received_at timestamp(6) not null,
    constraint fk_robot_action_event_execution foreign key (action_instance_id)
        references robot_action_execution (action_instance_id)
);

create index ix_robot_action_event_execution
    on robot_action_event (action_instance_id, event_sequence);

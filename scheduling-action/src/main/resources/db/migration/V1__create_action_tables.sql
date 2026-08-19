create table action_draft (
    id varchar(36) primary key,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    revision bigint not null,
    status varchar(32) not null,
    definition_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_action_draft_key_version unique (action_key, action_version)
);

create table action_release (
    id varchar(36) primary key,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    status varchar(32) not null,
    compiler_version varchar(32) not null,
    definition_json longtext not null,
    plan_json longtext not null,
    canonical_json longtext not null,
    plan_hash varchar(64) not null,
    change_summary varchar(1000) not null,
    published_at timestamp(6) not null,
    deprecated_at timestamp(6),
    constraint uk_action_release_key_version unique (action_key, action_version)
);

create table action_execution (
    action_instance_id varchar(128) primary key,
    robot_id varchar(128) not null,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    plan_hash varchar(64) not null,
    state varchar(32) not null,
    physical_result_known boolean not null,
    current_node_id varchar(1000),
    input_json longtext not null,
    context_json longtext not null,
    result_json longtext,
    error_json longtext,
    cancel_requested boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6)
);

create table action_execution_node (
    id varchar(36) primary key,
    action_instance_id varchar(128) not null,
    node_ordinal integer not null,
    execution_node_id varchar(1000) not null,
    source_path longtext not null,
    capability_key varchar(128) not null,
    capability_version varchar(32) not null,
    state varchar(32) not null,
    attempt integer not null,
    device_command_id varchar(128) not null,
    resolved_input_json longtext not null,
    output_json longtext,
    evidence_json longtext,
    error_json longtext,
    started_at timestamp(6),
    completed_at timestamp(6),
    constraint fk_execution_node_execution foreign key (action_instance_id)
        references action_execution (action_instance_id),
    constraint uk_execution_node_ordinal unique (action_instance_id, node_ordinal)
);

create index ix_action_draft_entry on action_draft (action_key, action_version);
create index ix_action_release_status on action_release (status, action_key, action_version);
create index ix_action_execution_state on action_execution (state, updated_at);

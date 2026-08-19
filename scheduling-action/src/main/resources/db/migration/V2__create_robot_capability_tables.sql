create table atomic_capability (
    id varchar(36) primary key,
    capability_key varchar(128) not null,
    capability_version varchar(32) not null,
    schema_hash varchar(128) not null,
    input_schema_json longtext not null,
    output_schema_json longtext not null,
    resources_json longtext not null,
    side_effect varchar(32) not null,
    retry_safety varchar(32) not null,
    safety_critical boolean not null,
    requires_motion_safety_parameters boolean not null,
    active boolean not null,
    synced_at timestamp(6) not null,
    constraint uk_atomic_capability_identity unique (capability_key, capability_version)
);

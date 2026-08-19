alter table action_execution
    add column workflow_instance_id varchar(128) null after action_version,
    add column workflow_node_instance_id varchar(128) null after workflow_instance_id;

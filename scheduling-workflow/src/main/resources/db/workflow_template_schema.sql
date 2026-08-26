create table if not exists workflow_template (
    id bigint not null auto_increment comment '主键',
    template_number varchar(64) not null comment '模板编号',
    template_name varchar(128) not null comment '模板名称',
    applicable_object varchar(64) null comment '适用对象',
    bpmn_xml longtext not null comment 'bpmn.js导出的完整BPMN XML',
    editor_data json null comment '页面画布及自定义属性JSON快照',
    deployment_id varchar(64) null comment 'Flowable部署ID',
    process_definition_id varchar(128) null comment 'Flowable流程定义ID',
    deployed_version int null comment '已部署流程定义版本',
    publish_status varchar(16) not null default 'DRAFT' comment 'DRAFT草稿/PUBLISHED已发布',
    created_at datetime not null comment '创建时间',
    updated_at datetime not null comment '更新时间',
    primary key (id),
    unique key uk_workflow_template_number (template_number),
    key ix_workflow_template_definition (process_definition_id)
) engine=InnoDB default charset=utf8mb4 comment='BPMN工作流模板';

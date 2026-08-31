-- 流程节点与新版 Action 三字段执行契约的关联字段。
-- 执行前请确认 workflow 服务已停止，并完成 flow_node 表备份。

alter table flow_node
    add column action_definition_id varchar(36) null comment 'Action定义ID' after node_code,
    add column action_instance_id varchar(128) null comment 'Action执行实例ID' after action_definition_id,
    add unique key uk_flow_node_action_instance (action_instance_id),
    add key ix_flow_node_action_definition (action_definition_id);

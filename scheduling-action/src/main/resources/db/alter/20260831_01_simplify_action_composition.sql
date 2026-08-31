-- Action 子动作编排模型切换：不迁移旧定义、参数集或执行快照。
-- 前置条件：Action 服务已停止，且已确认不存在活动 Action。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 当前阶段允许清空开发执行数据；事件必须先于执行主记录删除。
delete from action_execution_event;
delete from action_execution;
delete from action_definition;

drop table action_parameter_set;

alter table action_definition
    drop index uk_action_definition_key,
    drop column action_key,
    drop column revision,
    drop column status,
    add column name varchar(128) not null after id,
    add column enabled boolean not null after name,
    add index ix_action_definition_name (name, id);

alter table action_execution
    drop index ix_action_execution_action_state,
    drop index ix_action_execution_parameter_state,
    drop column action_key,
    drop column action_revision,
    drop column parameter_set_id,
    drop column parameter_set_revision,
    drop column workflow_instance_id,
    drop column workflow_node_instance_id,
    drop column definition_snapshot_json,
    drop column parameter_snapshot_json,
    drop column error_mapping_snapshot_json,
    add column action_definition_id varchar(36) not null after action_instance_id,
    add index ix_action_execution_definition_state (action_definition_id, state, created_at);

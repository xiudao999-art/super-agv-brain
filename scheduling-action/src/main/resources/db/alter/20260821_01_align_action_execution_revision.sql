-- 对齐动态 Action 执行快照与当前 JPA 实体字段。
-- 执行前必须备份 action_execution 与 action_execution_event。
-- 本脚本仅适用于仍使用 action_version、且尚未增加 row_version 的过渡期数据库；只能执行一次。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 历史 action_version 保存的实际内容就是 Action 定义 revision，原位重命名可完整保留已有数据。
-- row_version 用于 Hibernate 乐观锁；历史记录统一从 0 开始，不改变任何业务执行状态。
alter table action_execution
    change column action_version action_revision bigint not null,
    add column row_version bigint not null default 0 after completed_at;


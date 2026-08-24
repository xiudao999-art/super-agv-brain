-- 新增厂家原始异常到平台业务异常的动态映射规则表。
-- 本脚本只创建配置表，不自动导入未经评审的厂家错误码；执行前必须备份并登记。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

create table action_error_mapping_rule (
    rule_id varchar(128) primary key,
    profile_id varchar(128) not null,
    priority integer not null,
    revision bigint not null,
    status varchar(32) not null,
    rule_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    index ix_action_error_mapping_status_priority (status, priority, rule_id),
    index ix_action_error_mapping_profile (profile_id, status, priority)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 厂家目录需要经过适配器开发、现场协议和安全策略评审后，再通过管理接口录入并启用。
-- 未命中的异常由执行快照内置的 5999 / DEVICE.UNMAPPED_FAULT 保守兜底。

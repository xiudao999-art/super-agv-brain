-- 新增主 Action / 子 Action 共用的动态入参 Schema 表。
-- 仅保存当前完整 Schema，不包含版本、发布状态、引用关系和历史快照。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

create table action_parameter_schema (
    id varchar(36) not null,
    owner_type varchar(32) not null,
    owner_key varchar(128) not null,
    schema_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint uk_action_parameter_schema_owner unique (owner_type, owner_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

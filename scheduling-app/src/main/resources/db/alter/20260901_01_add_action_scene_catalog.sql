-- 为已有数据库新增 Action 页面业务场景目录。
-- 程序只读此表，建表和目录内容变更均由数据库管理员人工执行。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

create table action_scene_catalog_item (
    id bigint not null auto_increment,
    item_type varchar(16) not null,
    scene_code varchar(32) not null,
    item_code varchar(128) not null,
    display_name varchar(128) not null,
    sort_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    primary key (id),
    constraint uk_action_scene_catalog_item unique (item_type, scene_code, item_code),
    constraint ck_action_scene_catalog_item_type check (item_type in ('SCENE', 'OPERATION')),
    constraint ck_action_scene_catalog_scene_key check (item_type <> 'SCENE' or item_code = scene_code),
    key ix_action_scene_catalog_scenes (item_type, enabled, sort_order, item_code),
    key ix_action_scene_catalog_operations (scene_code, item_type, enabled, sort_order, item_code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

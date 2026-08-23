-- 坤灵调度系统实验室配置三表初始化脚本
-- 前置条件：调度基础库及 location 表已存在；location 冗余字段请另行执行 db/alter 下的增量脚本。
-- 应用不会自动执行本脚本；执行前必须确认目标数据库、完成备份并显式选择 schema。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 空间身份、地图和版本状态合并保存，避免为低频配置拆分过多表。
create table lab_config (
    id bigint not null auto_increment,
    space_id varchar(36) not null,
    space_code varchar(64) not null,
    space_name varchar(128) not null,
    map_name varchar(128) not null,
    map_version varchar(64) not null,
    map_file_ref varchar(512) not null,
    revision integer not null,
    status varchar(16) not null,
    published_at timestamp(6) null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    -- NULL 不参与唯一冲突，以下生成列用于保证一个空间最多一个草稿和一个已发布版本。
    initial_space_code varchar(64) generated always as (
        case when revision = 1 then space_code else null end
    ) stored,
    draft_space_id varchar(36) generated always as (
        case when status = 'DRAFT' then space_id else null end
    ) stored,
    published_space_id varchar(36) generated always as (
        case when status = 'PUBLISHED' then space_id else null end
    ) stored,
    primary key (id),
    constraint uk_lab_config_space_revision unique (space_id, revision),
    constraint uk_lab_config_initial_code unique (initial_space_code),
    constraint uk_lab_config_single_draft unique (draft_space_id),
    constraint uk_lab_config_single_published unique (published_space_id),
    index ix_lab_config_space_status (space_id, status, revision)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 节点、机台、机台点位共用一张对象表；kind 决定各坐标和关联字段的业务含义。
create table lab_config_object (
    id bigint not null auto_increment,
    config_id bigint not null,
    parent_id bigint null,
    location_id bigint null,
    nav_object_id bigint null,
    code varchar(64) not null,
    name varchar(128) not null,
    kind varchar(32) not null,
    type varchar(64) not null,
    coordinate_frame varchar(16) not null,
    x decimal(12,4) not null,
    y decimal(12,4) not null,
    z decimal(12,4) null,
    rx decimal(9,4) null,
    ry decimal(9,4) null,
    rz decimal(9,4) not null,
    primary key (id),
    constraint uk_lab_object_kind_code unique (config_id, kind, code),
    constraint fk_lab_object_config foreign key (config_id)
        references lab_config (id) on delete restrict on update restrict,
    constraint fk_lab_object_parent foreign key (parent_id)
        references lab_config_object (id) on delete restrict on update restrict,
    constraint fk_lab_object_nav foreign key (nav_object_id)
        references lab_config_object (id) on delete restrict on update restrict,
    constraint fk_lab_object_location foreign key (location_id)
        references location (id) on delete restrict on update restrict,
    index ix_lab_object_config_kind (config_id, kind, id),
    index ix_lab_object_parent (parent_id),
    index ix_lab_object_nav (nav_object_id),
    index ix_lab_object_location (location_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 图连接独立保存，避免在对象表中用成对字段表达多对多通行关系。
create table lab_config_link (
    id bigint not null auto_increment,
    config_id bigint not null,
    code varchar(64) not null,
    start_object_id bigint not null,
    end_object_id bigint not null,
    direction varchar(16) not null,
    speed_limit decimal(8,3) not null,
    primary key (id),
    constraint uk_lab_link_code unique (config_id, code),
    constraint fk_lab_link_config foreign key (config_id)
        references lab_config (id) on delete cascade on update restrict,
    constraint fk_lab_link_start foreign key (start_object_id)
        references lab_config_object (id) on delete restrict on update restrict,
    constraint fk_lab_link_end foreign key (end_object_id)
        references lab_config_object (id) on delete restrict on update restrict,
    index ix_lab_link_config (config_id, id),
    index ix_lab_link_start (start_object_id),
    index ix_lab_link_end (end_object_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

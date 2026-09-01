-- Action 页面业务场景目录初始数据。
-- 前置条件：已执行 db/alter/20260901_01_add_action_scene_catalog.sql，空库也可先执行 db/create 下的基线。
-- 本脚本可重复执行：只补齐缺失的唯一键，不覆盖人工修改的名称、顺序或启用状态。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

insert into action_scene_catalog_item
    (item_type, scene_code, item_code, display_name, sort_order, enabled, created_at, updated_at)
select
    seed.item_type,
    seed.scene_code,
    seed.item_code,
    seed.display_name,
    seed.sort_order,
    true,
    current_timestamp(6),
    current_timestamp(6)
from (
    select 'SCENE' as item_type, 'HOME' as scene_code, 'HOME' as item_code, '回零' as display_name, 10 as sort_order
    union all select 'SCENE', 'PICK', 'PICK', '抓取', 20
    union all select 'SCENE', 'PLACE', 'PLACE', '放置', 30
    union all select 'SCENE', 'MOVE', 'MOVE', '移动', 40
    union all select 'SCENE', 'CAPTURE', 'CAPTURE', '拍照', 50

    union all select 'OPERATION', 'HOME', 'MOVE_TO_POSE', '移动到位姿', 10
    union all select 'OPERATION', 'HOME', 'ARM_VERIFY_HOME', '机械臂回零确认', 20

    union all select 'OPERATION', 'PICK', 'MOVE_TO_POSE', '移动到位姿', 10
    union all select 'OPERATION', 'PICK', 'VISION.VERIFY_MATERIAL', '视觉物料确认', 20
    union all select 'OPERATION', 'PICK', 'GRIP.OPEN', '夹爪打开', 30
    union all select 'OPERATION', 'PICK', 'GRIP.CLOSE', '夹爪闭合', 40
    union all select 'OPERATION', 'PICK', 'GRIP.VERIFY_LOAD', '夹爪负载确认', 50

    union all select 'OPERATION', 'PLACE', 'MOVE_TO_POSE', '移动到位姿', 10
    union all select 'OPERATION', 'PLACE', 'GRIP.OPEN', '夹爪打开', 20
    union all select 'OPERATION', 'PLACE', 'VISION.VERIFY_PLACEMENT', '视觉放置确认', 30

    union all select 'OPERATION', 'MOVE', 'MOVE_TO_MAP_POINT', '移动到地图点位', 10
    union all select 'OPERATION', 'MOVE', 'CHASSIS_VERIFY_STOPPED', '底盘停稳确认', 20

    union all select 'OPERATION', 'CAPTURE', 'VISION.CAPTURE', '视觉拍照', 10
) seed
where not exists (
    select 1
    from action_scene_catalog_item existing
    where existing.item_type = seed.item_type
      and existing.scene_code = seed.scene_code
      and existing.item_code = seed.item_code
);

-- 执行后可按接口查询顺序核对全部目录项。
select item_type, scene_code, item_code, display_name, sort_order, enabled
from action_scene_catalog_item
order by item_type, scene_code, sort_order, item_code;

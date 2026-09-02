-- Action 参数 Schema 少量演示数据。
-- 前置条件：已执行 db/alter/20260831_01_add_action_parameter_schema.sql。
-- 本脚本可重复执行：同一 owner_type + owner_key 已存在时只覆盖 schema_json。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

-- 为数据库中第一个主 Action 配置两个通用业务参数；action_definition 为空时本段不会插入数据。
insert into action_parameter_schema
    (id, owner_type, owner_key, schema_json, created_at, updated_at)
select
    uuid(),
    'MAIN_ACTION',
    action_definition.id,
    '{"fields":[{"key":"taskRemark","label":"任务备注","dataType":"STRING","required":false,"defaultValue":null,"unit":null,"description":"本次任务的可选备注","minimum":null,"maximum":null,"enumValues":[],"sort":10},{"key":"priority","label":"任务优先级","dataType":"ENUM","required":false,"defaultValue":"NORMAL","unit":null,"description":"本次任务使用的优先级","minimum":null,"maximum":null,"enumValues":["LOW","NORMAL","HIGH"],"sort":20}]}',
    current_timestamp(6),
    current_timestamp(6)
from action_definition
order by action_definition.name, action_definition.id
limit 1
on duplicate key update
    schema_json = values(schema_json),
    updated_at = values(updated_at);

-- 两个稳定 operation 的子 Action 参数。
insert into action_parameter_schema
    (id, owner_type, owner_key, schema_json, created_at, updated_at)
values
    (
        uuid(),
        'SUB_ACTION',
        'MOVE_TO_MAP_POINT',
        '{"fields":[{"key":"pointName","label":"地图点名称","dataType":"STRING","required":true,"defaultValue":null,"unit":null,"description":"底盘地图中已经存在的目标点名称","minimum":null,"maximum":null,"enumValues":[],"sort":10},{"key":"maxSpeed","label":"最大速度","dataType":"DECIMAL","required":false,"defaultValue":0.6,"unit":"m/s","description":"本次移动允许的最大线速度","minimum":0.05,"maximum":2,"enumValues":[],"sort":20}]}',
        current_timestamp(6),
        current_timestamp(6)
    ),
    (
        uuid(),
        'SUB_ACTION',
        'VISION.CAPTURE',
        '{"fields":[{"key":"cameraId","label":"相机标识","dataType":"STRING","required":true,"defaultValue":null,"unit":null,"description":"执行拍照的相机稳定标识","minimum":null,"maximum":null,"enumValues":[],"sort":10},{"key":"saveImage","label":"保存图片","dataType":"BOOLEAN","required":false,"defaultValue":true,"unit":null,"description":"是否保存本次拍照结果","minimum":null,"maximum":null,"enumValues":[],"sort":20}]}',
        current_timestamp(6),
        current_timestamp(6)
    )
on duplicate key update
    schema_json = values(schema_json),
    updated_at = values(updated_at);

-- 执行后可直接查看写入结果及每个 Schema 的字段数量。
select
    owner_type,
    owner_key,
    json_length(cast(schema_json as json), '$.fields') as field_count,
    updated_at
from action_parameter_schema
where owner_type = 'MAIN_ACTION'
   or owner_key in ('MOVE_TO_MAP_POINT', 'VISION.CAPTURE')
order by owner_type, owner_key;

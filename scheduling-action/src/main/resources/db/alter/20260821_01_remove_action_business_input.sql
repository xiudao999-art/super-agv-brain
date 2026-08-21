-- Action 业务入参已移除：设备联调参数集成为唯一运行参数来源。
--
-- 执行要求：
-- 1. 本脚本只执行一次，执行前必须备份 action_definition 与 action_execution。
-- 2. 必须先执行 20260820_01_dynamic_action_package.sql。
-- 3. 若旧 inputSchema 与 parameterSchema 存在同名根字段，以旧 inputSchema 的定义为准。
-- 4. 迁移后请为新增的必填参数补齐设备联调参数集，再进行实机联调。

-- 将旧业务入参 Schema 合并到设备联调参数 Schema，同时把步骤绑定改为 $parameters.*。
UPDATE action_definition
SET definition_json = REPLACE(
    CAST(
        JSON_SET(
            JSON_REMOVE(CAST(definition_json AS JSON), '$.inputSchema'),
            '$.parameterSchema',
            JSON_MERGE_PATCH(
                COALESCE(JSON_EXTRACT(definition_json, '$.parameterSchema'), JSON_OBJECT()),
                COALESCE(JSON_EXTRACT(definition_json, '$.inputSchema'), JSON_OBJECT())
            )
        ) AS CHAR CHARACTER SET utf8mb4
    ),
    '$input.',
    '$parameters.'
);

-- 历史执行证据仍保留定义、参数、最终动作包和逐步骤结果；业务输入快照不再属于模型。
ALTER TABLE action_execution
    DROP COLUMN input_snapshot_json;

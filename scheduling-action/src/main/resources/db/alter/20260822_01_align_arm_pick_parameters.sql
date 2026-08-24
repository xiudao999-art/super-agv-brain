-- ARM.PICK 联调参数与 cnet8 完整动作包协议对齐。
--
-- 执行要求：
-- 1. 本脚本只执行一次，执行前备份 action_definition 与 action_parameter_set。
-- 2. 执行期间必须停止 Action 配置写入，并确认 ARM.PICK 没有活动执行实例。
-- 3. 本脚本不会下发设备动作；迁移后 ARM.PICK 回到 DRAFT，必须核对预览后再手动启用。
-- 4. 位姿默认值来自当前 cnet8 ARM.PICK.Templates.json，仅用于开发联调；实机运行前必须重新示教确认。

START TRANSACTION;

-- 参数职责：poses 是唯一位姿来源；motion 只保存到位判定和坐标系参数。
UPDATE action_definition
SET definition_json = CAST(
        JSON_SET(
            CAST(definition_json AS JSON),
            '$.parameterSchema',
            CAST('{
              "station":{"type":"STRING","required":true},
              "poses":{"type":"OBJECT","required":true,"properties":{
                "safe":{"type":"OBJECT","required":true,"properties":{
                  "x":{"type":"NUMBER","required":true,"unit":"mm"},
                  "y":{"type":"NUMBER","required":true,"unit":"mm"},
                  "z":{"type":"NUMBER","required":true,"unit":"mm"},
                  "rx":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "ry":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "rz":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360}
                }},
                "approach":{"type":"OBJECT","required":true,"properties":{
                  "x":{"type":"NUMBER","required":true,"unit":"mm"},
                  "y":{"type":"NUMBER","required":true,"unit":"mm"},
                  "z":{"type":"NUMBER","required":true,"unit":"mm"},
                  "rx":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "ry":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "rz":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360}
                }},
                "pick":{"type":"OBJECT","required":true,"properties":{
                  "x":{"type":"NUMBER","required":true,"unit":"mm"},
                  "y":{"type":"NUMBER","required":true,"unit":"mm"},
                  "z":{"type":"NUMBER","required":true,"unit":"mm"},
                  "rx":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "ry":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "rz":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360}
                }},
                "retreat":{"type":"OBJECT","required":true,"properties":{
                  "x":{"type":"NUMBER","required":true,"unit":"mm"},
                  "y":{"type":"NUMBER","required":true,"unit":"mm"},
                  "z":{"type":"NUMBER","required":true,"unit":"mm"},
                  "rx":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "ry":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360},
                  "rz":{"type":"NUMBER","required":true,"unit":"degree","minimum":-360,"maximum":360}
                }}
              }},
              "motion":{"type":"OBJECT","required":true,"properties":{
                "positionToleranceMm":{"type":"NUMBER","required":true,"unit":"mm","minimum":0.1,"maximum":20},
                "angleToleranceDeg":{"type":"NUMBER","required":true,"unit":"degree","minimum":0.1,"maximum":30},
                "settleMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":0,"maximum":5000},
                "timeoutMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":1000,"maximum":60000},
                "pollMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":10,"maximum":1000},
                "frame":{"type":"STRING","required":true,"enumValues":["BASE"]}
              }},
              "vision":{"type":"OBJECT","required":true,"properties":{
                "recipe":{"type":"STRING","required":true},
                "cameraId":{"type":"STRING","required":true},
                "exposureMs":{"type":"NUMBER","required":true,"unit":"ms","minimum":0.1,"maximum":1000},
                "gain":{"type":"NUMBER","required":true,"minimum":0,"maximum":32},
                "timeoutMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":100,"maximum":60000},
                "outputFormat":{"type":"STRING","required":true,"enumValues":["png","jpg","jpeg"]},
                "simulatedPass":{"type":"BOOLEAN","required":true}
              }},
              "gripper":{"type":"OBJECT","required":true,"properties":{
                "profileName":{"type":"STRING","required":true},
                "openWidthMm":{"type":"NUMBER","required":true,"unit":"mm","minimum":0,"maximum":100},
                "openHoldMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":0,"maximum":5000},
                "emptyDetectedMinWidth":{"type":"NUMBER","required":true,"unit":"mm","minimum":0,"maximum":100},
                "closeWidthMm":{"type":"NUMBER","required":true,"unit":"mm","minimum":0,"maximum":100},
                "gripForce":{"type":"NUMBER","required":true,"unit":"N","minimum":0,"maximum":100},
                "closeHoldMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":0,"maximum":5000},
                "minDetectedWidth":{"type":"NUMBER","required":true,"unit":"mm","minimum":0,"maximum":100},
                "maxDetectedWidth":{"type":"NUMBER","required":true,"unit":"mm","minimum":0,"maximum":100},
                "holdCheckMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":0,"maximum":10000},
                "pollMs":{"type":"INTEGER","required":true,"unit":"ms","minimum":10,"maximum":1000},
                "requireForceFeedback":{"type":"BOOLEAN","required":true},
                "minForce":{"type":"NUMBER","required":true,"unit":"N","minimum":0,"maximum":100}
              }}
            }' AS JSON)
        ) AS CHAR CHARACTER SET utf8mb4
    ),
    status = 'DRAFT',
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE action_key = 'ARM.PICK';

-- 把历史 motion.*Pose 搬迁到 poses，并统一夹爪字段名；优先保留已经正确填写的新字段。
UPDATE action_parameter_set
SET values_json = CAST(
        JSON_OBJECT(
            'station', COALESCE(
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.station')), ''),
                'PICK_01'
            ),
            'poses', JSON_OBJECT(
                'safe', CASE
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.poses.safe.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.poses.safe')
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.motion.safePose.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.motion.safePose')
                    ELSE CAST('{"x":300,"y":0,"z":450,"rx":180,"ry":0,"rz":0}' AS JSON)
                END,
                'approach', CASE
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.poses.approach.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.poses.approach')
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.motion.approachPose.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.motion.approachPose')
                    ELSE CAST('{"x":400,"y":100,"z":250,"rx":180,"ry":0,"rz":0}' AS JSON)
                END,
                'pick', CASE
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.poses.pick.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.poses.pick')
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.motion.pickPose.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.motion.pickPose')
                    ELSE CAST('{"x":400,"y":100,"z":150,"rx":180,"ry":0,"rz":0}' AS JSON)
                END,
                'retreat', CASE
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.poses.retreat.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.poses.retreat')
                    WHEN JSON_TYPE(JSON_EXTRACT(values_json, '$.motion.retreatPose.x')) IN ('INTEGER', 'DOUBLE', 'DECIMAL')
                        THEN JSON_EXTRACT(values_json, '$.motion.retreatPose')
                    ELSE CAST('{"x":400,"y":100,"z":350,"rx":180,"ry":0,"rz":0}' AS JSON)
                END
            ),
            'motion', JSON_OBJECT(
                'positionToleranceMm', COALESCE(JSON_EXTRACT(values_json, '$.motion.positionToleranceMm'), 2),
                'angleToleranceDeg', COALESCE(JSON_EXTRACT(values_json, '$.motion.angleToleranceDeg'), 1),
                'settleMs', COALESCE(JSON_EXTRACT(values_json, '$.motion.settleMs'), 200),
                'timeoutMs', COALESCE(JSON_EXTRACT(values_json, '$.motion.timeoutMs'), 10000),
                'pollMs', COALESCE(JSON_EXTRACT(values_json, '$.motion.pollMs'), 50),
                'frame', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.motion.frame')), ''), 'BASE')
            ),
            'vision', JSON_OBJECT(
                'recipe', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.vision.recipe')), ''), 'MATERIAL'),
                'cameraId', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.vision.cameraId')), ''), 'CAM01'),
                'exposureMs', COALESCE(JSON_EXTRACT(values_json, '$.vision.exposureMs'), 18),
                'gain', COALESCE(JSON_EXTRACT(values_json, '$.vision.gain'), 1.4),
                'timeoutMs', COALESCE(JSON_EXTRACT(values_json, '$.vision.timeoutMs'), 5000),
                'outputFormat', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.vision.outputFormat')), ''), 'png'),
                'simulatedPass', COALESCE(JSON_EXTRACT(values_json, '$.vision.simulatedPass'), CAST('true' AS JSON))
            ),
            'gripper', JSON_OBJECT(
                'profileName', COALESCE(
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.gripper.profileName')), ''),
                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(values_json, '$.gripper.profile')), ''),
                    'DEFAULT_PICK'
                ),
                'openWidthMm', COALESCE(
                    JSON_EXTRACT(values_json, '$.gripper.openWidthMm'),
                    JSON_EXTRACT(values_json, '$.gripper.preOpenWidthMm'),
                    80
                ),
                'openHoldMs', COALESCE(
                    JSON_EXTRACT(values_json, '$.gripper.openHoldMs'),
                    JSON_EXTRACT(values_json, '$.gripper.holdMs'),
                    150
                ),
                'emptyDetectedMinWidth', COALESCE(JSON_EXTRACT(values_json, '$.gripper.emptyDetectedMinWidth'), 70),
                'closeWidthMm', COALESCE(
                    JSON_EXTRACT(values_json, '$.gripper.closeWidthMm'),
                    JSON_EXTRACT(values_json, '$.gripper.gripWidthMm'),
                    25
                ),
                'gripForce', COALESCE(JSON_EXTRACT(values_json, '$.gripper.gripForce'), 35),
                'closeHoldMs', COALESCE(
                    JSON_EXTRACT(values_json, '$.gripper.closeHoldMs'),
                    JSON_EXTRACT(values_json, '$.gripper.holdMs'),
                    150
                ),
                'minDetectedWidth', COALESCE(JSON_EXTRACT(values_json, '$.gripper.minDetectedWidth'), 5),
                'maxDetectedWidth', COALESCE(JSON_EXTRACT(values_json, '$.gripper.maxDetectedWidth'), 65),
                'holdCheckMs', COALESCE(JSON_EXTRACT(values_json, '$.gripper.holdCheckMs'), 500),
                'pollMs', COALESCE(JSON_EXTRACT(values_json, '$.gripper.pollMs'), 50),
                'requireForceFeedback', COALESCE(
                    JSON_EXTRACT(values_json, '$.gripper.requireForceFeedback'),
                    CAST('true' AS JSON)
                ),
                'minForce', COALESCE(JSON_EXTRACT(values_json, '$.gripper.minForce'), 1)
            )
        ) AS CHAR CHARACTER SET utf8mb4
    ),
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE action_key = 'ARM.PICK';

COMMIT;

-- 迁移完成后请在 Action 工作台生成最终动作包，逐项核对位姿、速度档位和夹爪参数，再启用 ARM.PICK。

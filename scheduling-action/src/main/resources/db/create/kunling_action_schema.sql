-- 坤灵调度系统 Action 模块全量初始化脚本
-- 适用范围：仅限全新数据库。已有数据库禁止重复执行，请使用 db/alter 下的增量脚本。
-- 执行前请先显式选择目标数据库，并确认已完成备份与环境核对。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

create table action_draft (
    id varchar(36) primary key,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    revision bigint not null,
    status varchar(32) not null,
    definition_json longtext not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint uk_action_draft_key_version unique (action_key, action_version),
    index ix_action_draft_entry (action_key, action_version)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_release (
    id varchar(36) primary key,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    status varchar(32) not null,
    compiler_version varchar(32) not null,
    definition_json longtext not null,
    plan_json longtext not null,
    canonical_json longtext not null,
    plan_hash varchar(64) not null,
    change_summary varchar(1000) not null,
    published_at timestamp(6) not null,
    deprecated_at timestamp(6),
    constraint uk_action_release_key_version unique (action_key, action_version),
    index ix_action_release_status (status, action_key, action_version)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table atomic_capability (
    id varchar(36) primary key,
    capability_key varchar(128) not null,
    contract_hash varchar(64) not null,
    input_schema_json longtext not null,
    output_schema_json longtext not null,
    resources_json longtext not null,
    side_effect varchar(32) not null,
    retry_safety varchar(32) not null,
    safety_critical boolean not null,
    requires_motion_safety_parameters boolean not null,
    active boolean not null,
    synced_at timestamp(6) not null,
    constraint uk_atomic_capability_key unique (capability_key)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution (
    action_instance_id varchar(128) primary key,
    robot_id varchar(128) not null,
    action_key varchar(128) not null,
    action_version varchar(32) not null,
    workflow_instance_id varchar(128),
    workflow_node_instance_id varchar(128),
    plan_hash varchar(64) not null,
    state varchar(32) not null,
    physical_result_known boolean not null,
    current_node_id varchar(1000),
    input_json longtext not null,
    context_json longtext not null,
    result_json longtext,
    error_json longtext,
    cancel_requested boolean not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    index ix_action_execution_state (state, updated_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table action_execution_node (
    id varchar(36) primary key,
    action_instance_id varchar(128) not null,
    node_ordinal integer not null,
    execution_node_id varchar(1000) not null,
    source_path longtext not null,
    capability_key varchar(128) not null,
    capability_contract_hash varchar(64),
    state varchar(32) not null,
    attempt integer not null,
    consume_id varchar(128) not null,
    resolved_input_json longtext not null,
    output_json longtext,
    evidence_json longtext,
    error_json longtext,
    started_at timestamp(6),
    completed_at timestamp(6),
    constraint fk_execution_node_execution foreign key (action_instance_id)
        references action_execution (action_instance_id),
    constraint uk_execution_node_ordinal unique (action_instance_id, node_ordinal)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table robot_action_execution (
    action_instance_id varchar(128) primary key,
    robot_id varchar(128) not null,
    device_command_id varchar(128) not null,
    action_type varchar(64) not null,
    action_version varchar(32) not null,
    template_version varchar(32) not null,
    request_hash varchar(64) not null,
    package_hash varchar(64) not null,
    workflow_instance_id varchar(128),
    workflow_node_instance_id varchar(128),
    state varchar(32) not null,
    physical_result_known boolean not null,
    timeout_ms integer not null,
    request_input_json longtext not null,
    command_input_json longtext not null,
    resolved_steps_json longtext,
    physical_result_json longtext,
    error_json longtext,
    dispatch_session_id varchar(64),
    dispatch_message_id varchar(64),
    last_event_message_id varchar(64),
    last_event_session_id varchar(64),
    last_event_sequence bigint,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    row_version bigint not null default 0,
    constraint uk_robot_action_device_command unique (device_command_id),
    index ix_robot_action_robot_state (robot_id, state, updated_at),
    index ix_robot_action_workflow (workflow_instance_id, workflow_node_instance_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table robot_action_event (
    message_id varchar(64) primary key,
    action_instance_id varchar(128) not null,
    robot_id varchar(128) not null,
    message_type varchar(32) not null,
    event_sequence bigint not null,
    event_state varchar(32) not null,
    payload_json longtext not null,
    received_at timestamp(6) not null,
    constraint fk_robot_action_event_execution foreign key (action_instance_id)
        references robot_action_execution (action_instance_id),
    index ix_robot_action_event_execution (action_instance_id, event_sequence)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

-- 初始数据由开发人员随本脚本一次性导入；应用启动时不会自动补写。
start transaction;

insert into action_draft (
    id, action_key, action_version, revision, status,
    definition_json, created_at, updated_at
) values
('10000000-0000-4000-8000-000000000001', 'ARM.HOME', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion": "1.0", "actionKey": "ARM.HOME", "version": "1.0.0", "displayName": "机械臂回零", "description": "确认底盘停止后回到安全零位并复核", "entryPoint": true, "scope": "TIANJIN",
  "inputSchema": {},
  "steps": [
    { "kind": "CAPABILITY", "stepId": "chassisInterlock", "displayName": "确认底盘停止", "capabilityKey": "chassis.verify.stopped", "with": {}, "gate": true },
    { "kind": "CAPABILITY", "stepId": "home", "displayName": "移动至零位", "timeoutMs": 15000, "capabilityKey": "arm.move.linear",
      "with": { "station": "GLOBAL", "point": "HOME", "poseRole": "HOME", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 0, "y": 0, "z": 500, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "SAFE" },
      "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyHome", "displayName": "复核零位", "capabilityKey": "arm.verify.home", "with": {}, "gate": true }
  ],
  "defaultPolicy": { "timeoutMs": 30000, "onFailure": { "strategy": "HOLD", "maxRetries": 0 } }
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000002', 'ARM.PICK', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion": "1.0", "actionKey": "ARM.PICK", "version": "1.0.0", "displayName": "机械臂抓取", "description": "天津标准单件抓取动作", "entryPoint": true, "scope": "TIANJIN",
  "inputSchema": { "station": { "type": "STRING", "required": true }, "point": { "type": "STRING", "required": true } },
  "steps": [
    { "kind": "CAPABILITY", "stepId": "safe", "displayName": "进入安全位", "capabilityKey": "arm.move.linear", "timeoutMs": 15000,
      "with": { "station": "$input.station", "point": "$input.point", "poseRole": "SAFE", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 300, "y": 0, "z": 450, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "SAFE" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "approach", "displayName": "进入接近位", "capabilityKey": "arm.move.linear", "timeoutMs": 15000,
      "with": { "station": "$input.station", "point": "$input.point", "poseRole": "APPROACH", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 400, "y": 100, "z": 250, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "NORMAL" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyMaterial", "displayName": "确认物料", "capabilityKey": "vision.verify.material",
      "with": { "station": "$input.station", "recipe": "MATERIAL", "cameraId": "CAM01", "exposureMs": 18, "gain": 1.4, "timeoutMs": 5000, "outputFormat": "png", "simulatedPass": true }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "preOpen", "displayName": "预张开夹爪", "capabilityKey": "gripper.open",
      "with": { "targetWidthMm": 80, "holdMs": 150, "minDetectedWidth": 70 }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "moveToPick", "displayName": "移动至抓取位", "capabilityKey": "arm.move.linear", "timeoutMs": 15000,
      "with": { "station": "$input.station", "point": "$input.point", "poseRole": "PICK", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 400, "y": 100, "z": 150, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "SENSITIVE" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "closeGripper", "displayName": "闭合夹爪", "capabilityKey": "gripper.close",
      "with": { "targetWidthMm": 25, "holdMs": 150, "minDetectedWidth": 5, "maxDetectedWidth": 65, "gripForce": 35 }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyLoad", "displayName": "确认夹持", "capabilityKey": "gripper.verify.load",
      "with": { "minDetectedWidth": 5, "maxDetectedWidth": 65, "stableForMs": 500, "pollMs": 50, "requireForceFeedback": true, "minForce": 1, "expectedDetected": true }, "onFailure": { "strategy": "RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "retreat", "displayName": "撤回安全区", "capabilityKey": "arm.move.linear", "timeoutMs": 15000,
      "with": { "station": "$input.station", "point": "$input.point", "poseRole": "RETREAT", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 400, "y": 100, "z": 350, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "NORMAL" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true }
  ],
  "defaultPolicy": { "timeoutMs": 90000, "onFailure": { "strategy": "HOLD", "maxRetries": 0 } }
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000003', 'ARM.PICK_BATCH', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion":"1.0","actionKey":"ARM.PICK_BATCH","version":"1.0.0","displayName":"机械臂批量抓取","description":"公共进入一次，按槽位循环抓取，公共退出一次","entryPoint":true,"scope":"TIANJIN",
  "inputSchema":{"station":{"type":"STRING","required":true},"slots":{"type":"ARRAY","required":true,"items":{"type":"OBJECT","required":true,"properties":{"slotId":{"type":"STRING","required":true,"enumValues":["A","B","C","D","E","F"]}}}}},
  "steps":[
    {"kind":"CAPABILITY","stepId":"commonSafe","displayName":"公共安全位","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"SAFE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":300,"y":0,"z":450,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SAFE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
    {"kind":"CAPABILITY","stepId":"commonApproach","displayName":"公共接近位","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"APPROACH","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":400,"y":100,"z":250,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"NORMAL"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
    {"kind":"FOREACH","stepId":"slots","displayName":"六缓存位循环","items":"$input.slots","itemVariable":"$item","maxIterations":6,"orderBy":{"property":"slotId","direction":"ASCENDING"},"steps":[
      {"kind":"CAPABILITY","stepId":"slotVerify","displayName":"槽位物料确认","capabilityKey":"vision.verify.material","with":{"station":"$input.station","recipe":"MATERIAL","cameraId":"CAM01","exposureMs":18,"gain":1.4,"timeoutMs":5000,"outputFormat":"png","simulatedPass":true},"gate":true},
      {"kind":"CONDITION","stepId":"selectSlot","displayName":"选择槽位坐标","condition":{"operator":"EQUAL","left":"$item.slotId","right":"A"},"then":[
        {"kind":"CAPABILITY","stepId":"moveA","displayName":"移动至A槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"A","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":400,"y":100,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectB","displayName":"选择B槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"B"},"then":[
        {"kind":"CAPABILITY","stepId":"moveB","displayName":"移动至B槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"B","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":400,"y":150,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectC","displayName":"选择C槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"C"},"then":[
        {"kind":"CAPABILITY","stepId":"moveC","displayName":"移动至C槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"C","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":400,"y":200,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectD","displayName":"选择D槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"D"},"then":[
        {"kind":"CAPABILITY","stepId":"moveD","displayName":"移动至D槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"D","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":500,"y":100,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectE","displayName":"选择E槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"E"},"then":[
        {"kind":"CAPABILITY","stepId":"moveE","displayName":"移动至E槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"E","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":500,"y":150,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectF","displayName":"选择F槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"F"},"then":[
        {"kind":"CAPABILITY","stepId":"moveF","displayName":"移动至F槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"F","poseRole":"PICK","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":500,"y":200,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CAPABILITY","stepId":"invalidSlot","displayName":"拒绝未知槽位","capabilityKey":"system.fail","with":{"message":"slotId 必须为 A-F"},"gate":true}]}]}]}]}]}]},
      {"kind":"CAPABILITY","stepId":"slotGrip","displayName":"槽位夹取","capabilityKey":"gripper.close","with":{"targetWidthMm":25,"holdMs":150,"minDetectedWidth":5,"maxDetectedWidth":65,"gripForce":35},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
      {"kind":"CAPABILITY","stepId":"slotResultVerify","displayName":"确认槽位夹取","capabilityKey":"gripper.verify.load","with":{"minDetectedWidth":5,"maxDetectedWidth":65,"stableForMs":500,"pollMs":50,"requireForceFeedback":true,"minForce":1,"expectedDetected":true},"onFailure":{"strategy":"RETRY","maxRetries":1},"gate":true},
      {"kind":"CAPABILITY","stepId":"slotRetreatLocal","displayName":"槽位局部撤回","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"LOCAL_RETREAT","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":400,"y":100,"z":250,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"NORMAL"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
    ]},
    {"kind":"CAPABILITY","stepId":"commonRetreat","displayName":"公共安全退出","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"RETREAT","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":300,"y":0,"z":450,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SAFE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
  ],"defaultPolicy":{"timeoutMs":240000,"onFailure":{"strategy":"HOLD","maxRetries":0}}
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000004', 'ARM.PLACE', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion": "1.0", "actionKey": "ARM.PLACE", "version": "1.0.0", "displayName": "机械臂放置", "description": "天津标准单件放置动作", "entryPoint": true, "scope": "TIANJIN",
  "inputSchema": { "station": { "type": "STRING", "required": true }, "point": { "type": "STRING", "required": true } },
  "steps": [
    { "kind": "CAPABILITY", "stepId": "safe", "displayName": "进入安全位", "capabilityKey": "arm.move.linear", "with": { "station": "$input.station", "point": "$input.point", "poseRole": "SAFE", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 300, "y": 0, "z": 450, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "SAFE" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "approach", "displayName": "进入接近位", "capabilityKey": "arm.move.linear", "with": { "station": "$input.station", "point": "$input.point", "poseRole": "APPROACH", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 600, "y": -100, "z": 250, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "NORMAL" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyDestination", "displayName": "确认放置区", "capabilityKey": "vision.verify.placement", "with": { "station": "$input.station", "recipe": "PLACEMENT_BEFORE", "cameraId": "CAM01", "exposureMs": 15, "gain": 1.1, "timeoutMs": 5000, "outputFormat": "png", "simulatedPass": true }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "moveToPlace", "displayName": "移动至放置位", "capabilityKey": "arm.move.linear", "with": { "station": "$input.station", "point": "$input.point", "poseRole": "PLACE", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 600, "y": -100, "z": 150, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "SENSITIVE" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "release", "displayName": "释放物料", "capabilityKey": "gripper.open", "with": { "targetWidthMm": 80, "holdMs": 180, "minDetectedWidth": 70 }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyEmpty", "displayName": "确认夹爪已空", "capabilityKey": "gripper.verify.load", "with": { "minDetectedWidth": 70, "maxDetectedWidth": 100, "stableForMs": 300, "pollMs": 50, "requireForceFeedback": false, "minForce": 0, "expectedDetected": false }, "onFailure": { "strategy": "RETRY", "maxRetries": 1 }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "verifyPlaced", "displayName": "确认放置结果", "capabilityKey": "vision.verify.placement", "with": { "station": "$input.station", "recipe": "PLACEMENT_AFTER", "cameraId": "CAM01", "exposureMs": 15, "gain": 1.1, "timeoutMs": 5000, "outputFormat": "png", "simulatedPass": true }, "gate": true },
    { "kind": "CAPABILITY", "stepId": "retreat", "displayName": "撤回安全区", "capabilityKey": "arm.move.linear", "with": { "station": "$input.station", "point": "$input.point", "poseRole": "RETREAT", "pose": { "inlinePose": { "frame": "BASE", "unit": "MILLIMETER_DEGREE", "x": 600, "y": -100, "z": 350, "rx": 180, "ry": 0, "rz": 0 } }, "positionToleranceMm": 2, "angleToleranceDeg": 1, "settleMs": 200, "timeoutMs": 10000, "pollMs": 50, "speedProfile": "COMMISSIONING_LOW", "collisionProfile": "NORMAL" }, "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true }
  ],
  "defaultPolicy": { "timeoutMs": 90000, "onFailure": { "strategy": "HOLD", "maxRetries": 0 } }
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000005', 'ARM.PLACE_BATCH', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion":"1.0","actionKey":"ARM.PLACE_BATCH","version":"1.0.0","displayName":"机械臂批量放置","description":"公共进入一次，按槽位循环放置，公共退出一次","entryPoint":true,"scope":"TIANJIN",
  "inputSchema":{"station":{"type":"STRING","required":true},"slots":{"type":"ARRAY","required":true,"items":{"type":"OBJECT","required":true,"properties":{"slotId":{"type":"STRING","required":true,"enumValues":["A","B","C","D","E","F"]}}}}},
  "steps":[
    {"kind":"CAPABILITY","stepId":"commonSafe","displayName":"公共安全位","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"SAFE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":300,"y":0,"z":450,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SAFE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
    {"kind":"CAPABILITY","stepId":"commonApproach","displayName":"公共接近位","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"APPROACH","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":600,"y":-100,"z":250,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"NORMAL"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
    {"kind":"FOREACH","stepId":"slots","displayName":"六缓存位循环","items":"$input.slots","itemVariable":"$item","maxIterations":6,"orderBy":{"property":"slotId","direction":"ASCENDING"},"steps":[
      {"kind":"CAPABILITY","stepId":"slotVerify","displayName":"槽位放置区确认","capabilityKey":"vision.verify.placement","with":{"station":"$input.station","recipe":"PLACEMENT_BEFORE","cameraId":"CAM01","exposureMs":15,"gain":1.1,"timeoutMs":5000,"outputFormat":"png","simulatedPass":true},"gate":true},
      {"kind":"CONDITION","stepId":"selectSlot","displayName":"选择槽位坐标","condition":{"operator":"EQUAL","left":"$item.slotId","right":"A"},"then":[
        {"kind":"CAPABILITY","stepId":"moveA","displayName":"移动至A槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"A","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":600,"y":-100,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectB","displayName":"选择B槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"B"},"then":[
        {"kind":"CAPABILITY","stepId":"moveB","displayName":"移动至B槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"B","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":600,"y":-150,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectC","displayName":"选择C槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"C"},"then":[
        {"kind":"CAPABILITY","stepId":"moveC","displayName":"移动至C槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"C","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":600,"y":-200,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectD","displayName":"选择D槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"D"},"then":[
        {"kind":"CAPABILITY","stepId":"moveD","displayName":"移动至D槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"D","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":700,"y":-100,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectE","displayName":"选择E槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"E"},"then":[
        {"kind":"CAPABILITY","stepId":"moveE","displayName":"移动至E槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"E","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":700,"y":-150,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CONDITION","stepId":"selectF","displayName":"选择F槽","condition":{"operator":"EQUAL","left":"$item.slotId","right":"F"},"then":[
        {"kind":"CAPABILITY","stepId":"moveF","displayName":"移动至F槽","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"F","poseRole":"PLACE","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":700,"y":-200,"z":150,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SENSITIVE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
      ],"else":[{"kind":"CAPABILITY","stepId":"invalidSlot","displayName":"拒绝未知槽位","capabilityKey":"system.fail","with":{"message":"slotId 必须为 A-F"},"gate":true}]}]}]}]}]}]},
      {"kind":"CAPABILITY","stepId":"slotRelease","displayName":"槽位释放","capabilityKey":"gripper.open","with":{"targetWidthMm":80,"holdMs":180,"minDetectedWidth":70},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true},
      {"kind":"CAPABILITY","stepId":"slotResultVerify","displayName":"确认槽位释放","capabilityKey":"gripper.verify.load","with":{"minDetectedWidth":70,"maxDetectedWidth":100,"stableForMs":300,"pollMs":50,"requireForceFeedback":false,"minForce":0,"expectedDetected":false},"onFailure":{"strategy":"RETRY","maxRetries":1},"gate":true},
      {"kind":"CAPABILITY","stepId":"slotPlacedVerify","displayName":"确认槽位放置结果","capabilityKey":"vision.verify.placement","with":{"station":"$input.station","recipe":"PLACEMENT_AFTER","cameraId":"CAM01","exposureMs":15,"gain":1.1,"timeoutMs":5000,"outputFormat":"png","simulatedPass":true},"gate":true},
      {"kind":"CAPABILITY","stepId":"slotRetreatLocal","displayName":"槽位局部撤回","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"LOCAL_RETREAT","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":600,"y":-100,"z":250,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"NORMAL"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
    ]},
    {"kind":"CAPABILITY","stepId":"commonRetreat","displayName":"公共安全退出","capabilityKey":"arm.move.linear","with":{"station":"$input.station","point":"COMMON","poseRole":"RETREAT","pose":{"inlinePose":{"frame":"BASE","unit":"MILLIMETER_DEGREE","x":300,"y":0,"z":450,"rx":180,"ry":0,"rz":0}},"positionToleranceMm":2,"angleToleranceDeg":1,"settleMs":200,"timeoutMs":10000,"pollMs":50,"speedProfile":"COMMISSIONING_LOW","collisionProfile":"SAFE"},"onFailure":{"strategy":"VERIFY_BEFORE_RETRY","maxRetries":1},"gate":true}
  ],"defaultPolicy":{"timeoutMs":240000,"onFailure":{"strategy":"HOLD","maxRetries":0}}
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000006', 'MOVE', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion": "1.0", "actionKey": "MOVE", "version": "1.0.0", "displayName": "底盘移动", "description": "海康底盘移动至位置目录中的目标点", "entryPoint": true, "scope": "TIANJIN",
  "inputSchema": {
    "target": { "type": "STRING", "required": true },
    "port": { "type": "STRING", "required": true },
    "speed": { "type": "NUMBER", "required": true, "unit": "m/s" }
  },
  "steps": [{
    "kind": "CAPABILITY", "stepId": "move", "displayName": "移动至目标点", "timeoutMs": 60000,
    "capabilityKey": "chassis.move",
    "with": { "target": "$input.target", "port": "$input.port", "speed": "$input.speed" },
    "onFailure": { "strategy": "VERIFY_BEFORE_RETRY", "maxRetries": 1 }, "gate": true
  }],
  "defaultPolicy": { "timeoutMs": 60000, "onFailure": { "strategy": "HOLD", "maxRetries": 0 } }
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
('10000000-0000-4000-8000-000000000007', 'VISION.CAPTURE', '1.0.0', 1, 'DRAFT',
'{
  "schemaVersion": "1.0", "actionKey": "VISION.CAPTURE", "version": "1.0.0", "displayName": "视觉拍照", "description": "采集图像并返回图像与检测证据，不等待外部业务处理", "entryPoint": true, "scope": "TIANJIN",
  "inputSchema": {
    "station": { "type": "STRING", "required": true }, "recipe": { "type": "STRING", "required": true }, "cameraId": { "type": "STRING", "required": true },
    "exposureMs": { "type": "NUMBER", "required": true, "unit": "ms" }, "gain": { "type": "NUMBER", "required": true }, "timeoutMs": { "type": "INTEGER", "required": true, "unit": "ms" },
    "outputFormat": { "type": "STRING", "required": true }, "simulatedPass": { "type": "BOOLEAN", "required": true }
  },
  "outputSchema": { "imageUri": { "type": "STRING" }, "confirmed": { "type": "BOOLEAN" } },
  "steps": [{ "kind": "CAPABILITY", "stepId": "capture", "displayName": "采集图像", "capabilityKey": "vision.capture",
    "with": { "station": "$input.station", "recipe": "$input.recipe", "cameraId": "$input.cameraId", "exposureMs": "$input.exposureMs", "gain": "$input.gain", "timeoutMs": "$input.timeoutMs", "outputFormat": "$input.outputFormat", "simulatedPass": "$input.simulatedPass" },
    "onFailure": { "strategy": "RETRY", "maxRetries": 1 }, "gate": true }],
  "defaultPolicy": { "timeoutMs": 15000, "onFailure": { "strategy": "HOLD", "maxRetries": 0 } }
}', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

commit;


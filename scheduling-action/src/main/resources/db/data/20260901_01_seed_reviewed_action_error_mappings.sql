-- 导入《坤灵机器人统一异常与动作包策略评审表_动态Action更新版.xlsx》中
-- “设备错误映射”Sheet 已标记为 REVIEWED 且当前运行时支持 EXACT 匹配的规则。
--
-- 工作簿状态到运行时状态的转换：REVIEWED -> ACTIVE。
-- 当前 Action 只使用 operation（可空）+ vendor + deviceType + rawCode 做精确匹配，
-- 因此不把型号、适配器或 capabilityPattern 强行写入规则；本批规则的 operation 为空，
-- 表示同一设备原始码在该设备类型下具有一致业务语义。
--
-- 本批明确不导入：
-- 1. HUAYAN-ARM-100：DRAFT，且 RANGE 匹配尚未实现；
-- 2. GRIPPER-001：DRAFT，厂商仍为占位值；
-- 3. SCCAMERA-001 / SCCAMERA-002：DRAFT，尚未完成厂家手册评审；
-- 4. GLOBAL-FALLBACK：5999 兜底由 BusinessErrorMappingEngine 内置，不是动态精确规则。
--
-- 脚本特性：
-- 1. 可重复执行；已存在且内容一致的规则保持不变；
-- 2. 同 rule_id 内容不同、或存在相同核心匹配键的 ACTIVE 规则时立即拒绝执行；
-- 3. 不覆盖人工修改或停用过的规则。
--
-- 执行前必须：选择目标库、完成备份、停止 Action 配置写入，并核对现场协议中的原始码。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

drop temporary table if exists reviewed_action_error_mapping_seed;

create temporary table reviewed_action_error_mapping_seed (
    rule_id varchar(128) primary key,
    profile_id varchar(128) not null,
    priority integer not null,
    status varchar(32) not null,
    rule_json longtext not null
) engine = InnoDB;

insert into reviewed_action_error_mapping_seed (
    rule_id,
    profile_id,
    priority,
    status,
    rule_json
)
values
    (
        'HIK-CHASSIS-001',
        'HIKROBOT-CHASSIS',
        1000,
        'ACTIVE',
        json_object(
            'ruleId', 'HIK-CHASSIS-001',
            'profileId', 'HIKROBOT-CHASSIS',
            'priority', 1000,
            'match', json_object(
                'vendor', 'HIKROBOT',
                'deviceType', 'CHASSIS',
                'rawCode', 'NAV_TIMEOUT'
            ),
            'result', json_object(
                'businessCode', '3001',
                'businessMessage', '导航阻塞超时',
                'reasonCode', 'MOVE.OBSTACLE_TIMEOUT',
                'handlingConstraint', 'RETRYABLE',
                'handlingAdvice', '清障并确认底盘停止后按动作包策略处理'
            )
        )
    ),
    (
        'HIK-CHASSIS-002',
        'HIKROBOT-CHASSIS',
        1000,
        'ACTIVE',
        json_object(
            'ruleId', 'HIK-CHASSIS-002',
            'profileId', 'HIKROBOT-CHASSIS',
            'priority', 1000,
            'match', json_object(
                'vendor', 'HIKROBOT',
                'deviceType', 'CHASSIS',
                'rawCode', 'SOCKET_CLOSED'
            ),
            'result', json_object(
                'businessCode', '5003',
                'businessMessage', '机器人离线',
                'reasonCode', 'ROBOT.OFFLINE',
                'handlingConstraint', 'MANUAL_INTERVENTION',
                'handlingAdvice', '恢复连接并核验当前任务与物理状态'
            )
        )
    ),
    (
        'HUAYAN-ARM-001',
        'HUAYAN-ARM',
        1000,
        'ACTIVE',
        json_object(
            'ruleId', 'HUAYAN-ARM-001',
            'profileId', 'HUAYAN-ARM',
            'priority', 1000,
            'match', json_object(
                'vendor', 'HUAYAN',
                'deviceType', 'ARM',
                'rawCode', '10006'
            ),
            'result', json_object(
                'businessCode', '3008',
                'businessMessage', '机械臂运动失败',
                'reasonCode', 'ARM.ENCODER_FAULT',
                'handlingConstraint', 'NON_RETRYABLE',
                'handlingAdvice', '检查编码器与线缆，按厂家手册复位/维修'
            )
        )
    );

drop procedure if exists apply_reviewed_action_error_mapping_seed;

delimiter $$

create procedure apply_reviewed_action_error_mapping_seed()
begin
    declare target_table_count integer default 0;
    declare seed_row_count integer default 0;
    declare invalid_json_count integer default 0;
    declare changed_rule_count integer default 0;
    declare duplicate_match_count integer default 0;
    declare applied_rule_count integer default 0;

    declare exit handler for sqlexception
    begin
        rollback;
        resignal;
    end;

    if database() is null then
        signal sqlstate '45000'
            set message_text = '未选择目标数据库，拒绝导入 Action 异常映射';
    end if;

    select count(*)
      into target_table_count
      from information_schema.tables
     where table_schema = database()
       and table_name = 'action_error_mapping_rule';

    if target_table_count <> 1 then
        signal sqlstate '45000'
            set message_text = '缺少 action_error_mapping_rule，请先执行 Action 建库或迁移脚本';
    end if;

    select count(*)
      into seed_row_count
      from reviewed_action_error_mapping_seed;

    if seed_row_count <> 3 then
        signal sqlstate '45000'
            set message_text = '评审映射种子数量异常，拒绝导入';
    end if;

    select count(*)
      into invalid_json_count
      from reviewed_action_error_mapping_seed
     where json_valid(rule_json) = 0;

    if invalid_json_count <> 0 then
        signal sqlstate '45000'
            set message_text = '评审映射种子包含无效 JSON，拒绝导入';
    end if;

    select count(*)
      into invalid_json_count
      from action_error_mapping_rule
     where binary status = binary 'ACTIVE'
       and json_valid(rule_json) = 0;

    if invalid_json_count <> 0 then
        signal sqlstate '45000'
            set message_text = '现有 ACTIVE 映射包含无效 JSON，请先修复';
    end if;

    -- 使用二进制精确比较，既与 Java equalsExact 语义一致，也避免目标表采用
    -- utf8mb4_unicode_ci 等历史排序规则时与当前连接排序规则发生冲突。
    -- 同一 rule_id 只允许幂等重放；禁止种子脚本覆盖人工调整或停用结果。
    select count(*)
      into changed_rule_count
      from action_error_mapping_rule current_rule
      join reviewed_action_error_mapping_seed seed
        on binary seed.rule_id = binary current_rule.rule_id
     where binary current_rule.profile_id <> binary seed.profile_id
        or current_rule.priority <> seed.priority
        or binary current_rule.status <> binary seed.status
        or json_valid(current_rule.rule_json) = 0
        or not (
            json_contains(current_rule.rule_json, seed.rule_json) = 1
            and json_contains(seed.rule_json, current_rule.rule_json) = 1
        );

    if changed_rule_count <> 0 then
        signal sqlstate '45000'
            set message_text = '同 rule_id 已存在不同内容，拒绝覆盖，请人工评审';
    end if;

    -- operation 缺省视为空；核心键为 operation + vendor + deviceType + rawCode。
    select count(*)
      into duplicate_match_count
      from action_error_mapping_rule current_rule
      join reviewed_action_error_mapping_seed seed
        on binary current_rule.rule_id <> binary seed.rule_id
       and binary current_rule.status = binary 'ACTIVE'
       and binary coalesce(
               json_unquote(json_extract(current_rule.rule_json, '$.match.operation')),
               ''
           ) = binary coalesce(
               json_unquote(json_extract(seed.rule_json, '$.match.operation')),
               ''
           )
       and binary json_unquote(json_extract(current_rule.rule_json, '$.match.vendor'))
           = binary json_unquote(json_extract(seed.rule_json, '$.match.vendor'))
       and binary json_unquote(json_extract(current_rule.rule_json, '$.match.deviceType'))
           = binary json_unquote(json_extract(seed.rule_json, '$.match.deviceType'))
       and binary json_unquote(json_extract(current_rule.rule_json, '$.match.rawCode'))
           = binary json_unquote(json_extract(seed.rule_json, '$.match.rawCode'));

    if duplicate_match_count <> 0 then
        signal sqlstate '45000'
            set message_text = '存在相同核心匹配键的 ACTIVE 规则，拒绝产生歧义';
    end if;

    start transaction;

    insert into action_error_mapping_rule (
        rule_id,
        profile_id,
        priority,
        revision,
        status,
        rule_json,
        created_at,
        updated_at
    )
    select seed.rule_id,
           seed.profile_id,
           seed.priority,
           1,
           seed.status,
           seed.rule_json,
           utc_timestamp(6),
           utc_timestamp(6)
      from reviewed_action_error_mapping_seed seed
     where not exists (
         select 1
           from action_error_mapping_rule current_rule
          where binary current_rule.rule_id = binary seed.rule_id
     );

    select count(*)
      into applied_rule_count
      from action_error_mapping_rule current_rule
      join reviewed_action_error_mapping_seed seed
        on binary seed.rule_id = binary current_rule.rule_id
     where binary current_rule.status = binary 'ACTIVE'
       and json_valid(current_rule.rule_json) = 1
       and json_contains(current_rule.rule_json, seed.rule_json) = 1
       and json_contains(seed.rule_json, current_rule.rule_json) = 1;

    if applied_rule_count <> seed_row_count then
        signal sqlstate '45000'
            set message_text = 'Action 异常映射导入后校验失败，已回滚';
    end if;

    commit;
end$$

delimiter ;

call apply_reviewed_action_error_mapping_seed();
drop procedure apply_reviewed_action_error_mapping_seed;

select rule_id,
       profile_id,
       priority,
       revision,
       status,
       json_unquote(json_extract(rule_json, '$.match.vendor')) as vendor,
       json_unquote(json_extract(rule_json, '$.match.deviceType')) as device_type,
       json_unquote(json_extract(rule_json, '$.match.rawCode')) as raw_code,
       json_unquote(json_extract(rule_json, '$.result.businessCode')) as business_code,
       json_unquote(json_extract(rule_json, '$.result.reasonCode')) as reason_code,
       json_unquote(json_extract(rule_json, '$.result.handlingConstraint')) as handling_constraint
  from action_error_mapping_rule
 where binary rule_id in ('HIK-CHASSIS-001', 'HIK-CHASSIS-002', 'HUAYAN-ARM-001')
 order by rule_id;

drop temporary table reviewed_action_error_mapping_seed;

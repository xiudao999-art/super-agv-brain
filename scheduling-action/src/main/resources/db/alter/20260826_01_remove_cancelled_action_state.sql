-- Action 2.0 最终线协议不再包含 CANCELLED。
--
-- 执行要求：
-- 1. 必须先执行 20260825_01_migrate_action_protocol_v2.sql，并停止 Action 服务写入。
-- 2. 执行前备份 action_execution，并在同结构测试库完成演练。
-- 3. 旧 CANCELLED 无法证明现场物理结果，统一安全收敛为 UNKNOWN_HOLD；不伪造成功或失败事实。

set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

drop procedure if exists assert_action_cancelled_cleanup_ready;

delimiter $$

create procedure assert_action_cancelled_cleanup_ready()
begin
    declare target_table_count integer default 0;
    declare required_column_count integer default 0;

    if database() is null then
        signal sqlstate '45000'
            set message_text = '未选择目标数据库，拒绝清理 Action CANCELLED 状态';
    end if;

    select count(*)
      into target_table_count
      from information_schema.tables
     where table_schema = database()
       and table_name = 'action_execution';

    select count(*)
      into required_column_count
      from information_schema.columns
     where table_schema = database()
       and table_name = 'action_execution'
       and column_name in (
           'state', 'physical_outcome', 'error_json',
           'updated_at', 'completed_at', 'row_version'
       );

    if target_table_count <> 1 or required_column_count <> 6 then
        signal sqlstate '45000'
            set message_text = 'action_execution 不是预期的 Action 2.0 结构，拒绝执行状态清理';
    end if;
end$$

delimiter ;

call assert_action_cancelled_cleanup_ready();
drop procedure assert_action_cancelled_cleanup_ready;

start transaction;

update action_execution
   set state = 'UNKNOWN_HOLD',
       physical_outcome = 'UNKNOWN',
       error_json = coalesce(
           error_json,
           '{"clientCode":70201,"message":"旧 CANCELLED 状态无法证明物理结果，已安全收敛为 UNKNOWN_HOLD"}'
       ),
       updated_at = current_timestamp(6),
       completed_at = coalesce(completed_at, current_timestamp(6)),
       row_version = row_version + 1
 where state = 'CANCELLED';

commit;

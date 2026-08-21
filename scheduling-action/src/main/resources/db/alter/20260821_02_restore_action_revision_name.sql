-- 修复部分环境被同步回过渡期列名 action_version 的问题。
-- 执行前必须备份 action_execution 与 action_execution_event。
-- 新结构已经使用 action_revision 时，本脚本安全跳过；只有旧列存在时才执行原位重命名。
set names utf8mb4 collate utf8mb4_0900_ai_ci;
set time_zone = '+00:00';

set @legacy_action_version_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'action_execution'
      and column_name = 'action_version'
);
set @current_action_revision_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'action_execution'
      and column_name = 'action_revision'
);

set @align_action_revision_sql = case
    when @legacy_action_version_count = 1 and @current_action_revision_count = 0
        then 'alter table action_execution change column action_version action_revision bigint not null'
    when @legacy_action_version_count = 0 and @current_action_revision_count = 1
        then 'select ''action_execution.action_revision already aligned'' as migration_result'
    else 'select * from action_execution_revision_column_state_is_invalid'
end;

prepare align_action_revision_statement from @align_action_revision_sql;
execute align_action_revision_statement;
deallocate prepare align_action_revision_statement;


-- 客户端进程重启后事件 sequence 可能重新计数；按 session 隔离顺序判断。
alter table robot_action_execution
    add column last_event_session_id varchar(64) null after last_event_message_id;

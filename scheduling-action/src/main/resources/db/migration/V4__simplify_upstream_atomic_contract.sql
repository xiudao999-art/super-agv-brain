-- 上游仅提供当前原子目录；下游以 capabilityKey 唯一存储并计算契约 Hash。
-- 若历史库存在同一 key 的多个版本，保留最近同步的一条，其余记录不再参与当前目录。
delete older
from atomic_capability older
join atomic_capability newer
  on older.capability_key = newer.capability_key
 and (
      older.synced_at < newer.synced_at
      or (older.synced_at = newer.synced_at and older.id < newer.id)
 );

alter table atomic_capability
    drop index uk_atomic_capability_identity,
    drop column capability_version,
    change column schema_hash contract_hash varchar(64) not null,
    add constraint uk_atomic_capability_key unique (capability_key);

-- 历史节点的版本号不能安全推导为新契约 Hash，因此迁移后保留为空。
-- 未完成实例会由启动恢复逻辑进入 UNKNOWN_HOLD，不会自动继续执行。
alter table action_execution_node
    drop column capability_version,
    change column device_command_id consume_id varchar(128) not null,
    add column capability_contract_hash varchar(64) null after capability_key;

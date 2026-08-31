# Action 数据库增量脚本

本目录只接收追加式人工 ALTER 脚本，已执行脚本禁止修改或删除。

- 全新空数据库：只执行 `../create/kunling_action_schema.sql`，不要再执行已包含在基线中的历史迁移。
- `20260825_01` 之前的文件是已发布的 1.0 历史迁移记录，保留用于数据库审计，不属于 2.0 运行时兼容代码。
- 已处于旧 Action 2.0 最终结构的开发数据库：备份并停止 Action 服务、确认没有活动
  Action 后执行 `20260831_01_simplify_action_composition.sql`。
- 其他历史结构不得猜测或跨号执行，必须先依据该环境的迁移登记确认前置结构。
- 2.0 上线后的新变更继续按时间顺序追加，并只执行该环境尚未登记的脚本。

`20260825_01_migrate_action_protocol_v2.sql` 会拒绝存在活动执行实例或结构不匹配的数据库；旧定义会保留但统一停用，必须重新配置为合法 2.0 定义后才能启用。

`20260826_01_remove_cancelled_action_state.sql` 将历史 `CANCELLED` 保守收敛为
`UNKNOWN_HOLD + UNKNOWN`；它只迁移历史数据，不增加运行时兼容分支。

`20260831_01_simplify_action_composition.sql` 是一次明确的不兼容切换：清空旧定义和执行数据，
删除参数集及所有配置快照字段，只保留实际下发的 `command_input_json` 作为执行证据。

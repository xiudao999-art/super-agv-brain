# 阶段 0–5 整体验收报告

- 验收日期：2026-08-19
- 工程：`kunling-scheduling`
- 结论：阶段 0–4 工程验收通过；阶段 5 的软件侧故障恢复项通过，真实上游与天津现场设备项待联调。

## 已确认范围

- 主 Action、全局组合动作、Canvas 编辑器和运行编排全部归属调度主应用的 `scheduling-action` 模块。
- 上游代码只读；本次没有修改上游工程。
- 数据库使用 MySQL，不使用原设计中的 SQLite。
- 一期不做权限、任意并行和脚本。
- Action 内不执行自动重试或跳过；异常策略后续由工作流接入。
- 允许配置真实 InlinePose，但坐标、单位、枚举和上下限必须通过上游输入 Schema 校验。

## 分阶段结论

| 阶段 | 结论 | 验收证据 |
|---|---|---|
| 0 契约与骨架 | 通过 | Spring Boot 模块化单体、独立 Action 模块、三接口上游端口、防腐 Adapter、ADR、MySQL 手工脚本 |
| 1 Compiler | 通过 | 四种节点、组合动作精确版本引用、原子目录快照、契约 Hash 固定、循环检测、参数递归校验、数值上下限、有界循环、条件白名单、计划 Hash、source path |
| 2 Runtime 与持久化 | 软件侧通过 | 顺序执行、单机器人进程内串行、consumeId、契约失配阻断、执行/节点日志、输入物化、结果证据、UNKNOWN_HOLD、重启恢复 |
| 3 动作迁移 | 工程侧通过 | 7 个天津动作 JSON 可解析且全部编译；Batch 固定最多 6 槽，公共进入/退出不随槽位重复配置 |
| 4 调度控制面 | 通过 | 草稿增删改、不可变发布、克隆新版本、下线、差异、依赖、快照、全局组合动作完整管理、Canvas 拖拽 |
| 5 天津现场验证 | 部分通过 | MySQL 实库启动、断上游阻断、重启转 HOLD 已通过；海康 AGV、华沿机械臂低速实机与断网联调待执行 |

## 自动化结果

- Java：16 项测试通过，0 失败，其中 Action 行为 13 项、模块接口 3 项。
- Canvas/前端：26 项测试通过，0 失败。
- 上游只读工程：`dotnet build KunlingRobotClient.sln`，0 警告、0 错误。
- Spring Boot 可执行 JAR：`scheduling-app/target/kunling-scheduling.jar` 构建成功。
- MySQL 8：提供一份全量 CREATE 脚本并包含 7 个天津标准 Action 草稿；后续由开发人员手工执行 ALTER。

## MySQL/API 集成验收

使用独立数据库 `kunling_action_acceptance`、`kunling_action_contract_v11_acceptance`、
`kunling_action_contract_upgrade_v11_acceptance` 和 `kunling_scheduling_acceptance` 完成：

1. 控制台首页返回 200 且 Canvas 存在。
2. 草稿创建、修订号更新、删除成功。
3. 测试组合动作编译并发布成功。
4. 主 Action 精确引用该组合动作，展开为原子节点并发布成功。
5. 组合动作下线后不再进入可引用目录；已发布主 Action 的快照保持不变。
6. 未配置上游 Adapter 时，目录同步和执行入口均返回 503，不会假装成功。
7. 模拟未完成实例后重启，状态转换为 `UNKNOWN_HOLD`、`physicalResultKnown=false`，没有自动重放。
8. 全新数据库连续应用 V1–V4 成功，目录表只保留 `capability_key + contract_hash`，执行节点使用 `consume_id`。
9. 带两个历史能力版本和旧执行节点数据的 V3 数据库升级到 V4 成功：保留最新目录记录、消费 ID 不变、旧版本列全部移除。
10. 模块化改造后，可执行主应用能够从 Action 模块 JAR 发现页面、控制器、5 个 JPA 仓储、4 个迁移及 7 个标准动作草稿。

## 尚不能签字的现场项

- 上游按契约提供真实原子能力目录、基于 consumeId 的持久去重提交和状态查询接口。
- 海康 AGV 与华沿机械臂的真实 Schema、单位、坐标系和软件限位确认。
- InlinePose 在 `COMMISSIONING_LOW` 下逐点校准。
- 真实动作中断网、上游重启、设备重启后的 `physicalResultKnown` 判定。
- 六缓存位 Batch 的实机顺序、公共进入/退出、抓放证据核验。
- 若部署多个下游实例，需要补充跨实例的机器人执行互斥；当前只有单实例进程内互斥，并要求上游同时保证单机器人命令不交错。

上游接口需求见 [upstream-integration-requirements.md](upstream-integration-requirements.md)。

# 上游团队交付包与验收清单 V1.1

本文可直接发送给上游团队。上游交付当前原子能力目录和原子动作执行能力；主 Action、组合动作、Canvas、Action 版本与运行编排全部属于调度系统的 Action 模块。

## 1. 交付目标

上游交付完成后，下游应能够：

1. 同步天津项目当前可用的原子能力目录；
2. 根据目录内容计算契约 Hash，编译并发布 Action；
3. 使用每个原子节点唯一的 `consumeId` 提交动作并查询结果；
4. 对重复消费持久去重，不重复驱动物理设备；
5. 在断网、超时、进程重启或结果不确定时得到明确的 `UNKNOWN`，由下游进入 HOLD；
6. 获取海康 AGV、华沿机械臂及配套夹爪/视觉动作的可追溯证据。

## 2. 一期必须交付的内容（P0）

| 编号 | 上游交付物 | 具体内容 | 下游验收结果 |
|---|---|---|---|
| D01 | 可部署的原子 Action 服务 | 测试环境地址、部署包或镜像、启动配置、健康检查、版本号 | 下游配置 Adapter 后可访问服务 |
| D02 | OpenAPI 3.x 接口文件 | 覆盖当前能力目录、按消费 ID 提交、按消费 ID 查询三类接口 | OpenAPI 与真实响应一致 |
| D03 | 天津原子能力目录 | `tianjin-atomic-capabilities.json`，包含完整 Schema、资源、副作用和安全属性，不包含原子能力版本号 | 目录同步成功，当前 7 个天津 Action 均可编译 |
| D04 | 唯一消费台账 | 持久化 `consumeId`、请求摘要、状态和厂商任务号；服务重启后仍可查询 | 重复消费不重复驱动；同 ID 不同请求被拒绝 |
| D05 | 原子动作状态机 | 六种状态及 `physicalResultKnown` 判定规则 | 未知结果不会伪装成成功或明确失败 |
| D06 | 单机器人命令串行保障 | 同一 `robotId` 的物理命令必须排队或明确拒绝 | 并发测试中不存在设备命令交错 |
| D07 | 海康 AGV 适配 | `chassis.move`、`chassis.verify.stopped` 及参数、状态、厂商任务号映射 | 低速移动、到位、失败、断网和重查通过 |
| D08 | 华沿机械臂适配 | `arm.move.linear`、`arm.verify.home` 及坐标系、姿态、单位、软限位和安全 Profile | `COMMISSIONING_LOW` 下逐点校准通过 |
| D09 | 夹爪和视觉能力 | 当前动作依赖的夹爪、拍照、物料确认和放置确认能力 | 单件抓放和六槽批量动作能力完整 |
| D10 | 错误码与证据字典 | 错误码、厂商错误码、是否可重查、处理建议和 evidence 字段 | 下游可以定位失败并展示物理证据 |
| D11 | 配置与运维说明 | 端口、超时、日志、数据保存、升级/回滚和负责人 | 测试环境可重复部署 |
| D12 | 自动化与现场报告 | 契约、重复消费、并发串行、重启恢复和天津实机测试记录 | P0 用例具备请求、响应、日志和设备证据 |

## 3. 必须实现的三个接口

```text
GET  /api/v1/atomic-capabilities
POST /api/v1/robots/{robotId}/atomic-actions
GET  /api/v1/atomic-actions/{consumeId}
```

完整字段见 [上游原子 Action 接口需求](upstream-integration-requirements.md)。一期不要求机器人可用性预检查接口，提交接口的受理或拒绝结果才是最终判断。

## 4. 唯一消费 ID 的强制语义

`consumeId` 同时承担唯一消费和去重职责，不再额外使用 `Idempotency-Key`。

上游必须满足：

- 数据库层唯一约束；
- 相同 ID、相同请求返回原记录；
- 相同 ID、不同请求返回 `409 Conflict`；
- 并发请求只能有一个创建成功；
- 服务重启后记录仍然存在；
- 可以直接按 ID 查询状态；
- 去重记录保存周期至少覆盖调度任务追溯周期。

下游生成规则：

```text
consumeId = actionInstanceId + ":" + nodeOrdinal
```

因此一个主 Action 中重复出现同一种原子能力时，每个节点仍有不同消费 ID；同一个节点因网络重放时保持相同 ID。

## 5. 天津一期最小原子能力

| capabilityKey | 类型 | 核心输入 | 证据示例 |
|---|---|---|---|
| `chassis.move` | 海康 AGV 物理动作 | target、port、speed | 厂商任务号、最终站点、到位状态 |
| `chassis.verify.stopped` | AGV 查询 | 无 | 当前速度、停止确认时间 |
| `arm.move.linear` | 华沿机械臂物理动作 | station、point、poseRole、pose、容差、Profile、超时 | 厂商任务号、最终六维位姿、到位偏差 |
| `arm.verify.home` | 机械臂查询 | 无 | 当前位姿、零位偏差 |
| `gripper.open` | 夹爪物理动作 | 目标宽度、保持时间、检测宽度 | 实际宽度、完成标记 |
| `gripper.close` | 夹爪物理动作 | 目标宽度、宽度范围、夹持力 | 实际宽度、实际力 |
| `gripper.verify.load` | 夹爪查询 | 宽度范围、稳定时间、力反馈、期望状态 | 检测宽度、检测力、物料状态 |
| `vision.capture` | 视觉查询 | 工位、配方、相机、曝光、增益、超时、格式 | 图片 URI、帧号、采集时间 |
| `vision.verify.material` | 视觉查询 | 同视觉参数 | 物料状态、置信度、图片 URI |
| `vision.verify.placement` | 视觉查询 | 同视觉参数 | 放置状态、检测结果、图片 URI |
| `system.fail` | 平台控制能力 | message | 标准失败码和消息 |

`system.fail` 不调用厂商 SDK。它当前用于把确定性非法配置分支转换为标准失败，后续可以由下游内置节点替代。

目录约束：

- 当前目录中 `capabilityKey` 唯一；
- 上游不提供原子能力 `version` 或 `schemaHash`；
- 下游根据目录内容计算 `contractHash`；
- Schema 使用 `STRING/NUMBER/INTEGER/BOOLEAN/OBJECT/ARRAY`；
- 参数明确 required、unit、枚举、对象属性、数组元素和数值范围；
- `arm.move.linear` 声明 `requiresMotionSafetyParameters=true`；
- 空目录不能用于表达“删除全部能力”。

## 6. 物理结果规则

| 状态 | physicalResultKnown | 必要条件 |
|---|---:|---|
| `SUCCEEDED` | `true` | 设备反馈和业务证据均确认达到目标 |
| `FAILED` | `true` | 已确认未达到目标，且当前物理状态明确 |
| `UNKNOWN` | `false` | 超时、断网、重启后无法确认最终结果 |
| `CANCELLED` | `true` | 已确认设备停止，且取消后物理状态明确 |

`SUCCEEDED + false` 是非法组合。取消命令发出但无法确认设备停止时，也必须返回 `UNKNOWN + false`。

## 7. 实机参数与安全资料

上游需提供并确认：

1. 海康地图、站点、端口和方向编码表；
2. AGV 速度单位、范围、到位容差和状态映射；
3. 华沿机械臂坐标系、姿态顺序、角度单位和工具/工件坐标配置；
4. x/y/z/rx/ry/rz 软件限位；
5. `COMMISSIONING_LOW`、`SAFE`、`NORMAL`、`SENSITIVE` 的真实参数；
6. 夹爪宽度、力和“有料/无料”判定；
7. 超时后设备是否可能继续运动以及复核方法；
8. 急停、安全门和底盘停止联锁的实现位置。

允许下游编辑真实坐标不等于上游直接透传坐标。上游在调用厂商 SDK 前仍必须校验坐标系、软限位、速度和联锁。

## 8. 建议交付目录

```text
upstream-delivery/
├─ api/upstream-atomic-action-v1.openapi.yaml
├─ capabilities/tianjin-atomic-capabilities.json
├─ docs/
│  ├─ consume-id-and-state-rules.md
│  ├─ error-and-evidence-catalog.md
│  ├─ hikvision-agv-mapping.md
│  ├─ huayan-arm-mapping.md
│  └─ deployment-and-operations.md
├─ deploy/
├─ tests/
│  ├─ contract-test-report.md
│  ├─ duplicate-consume-and-restart-report.md
│  └─ tianjin-site-test-report.md
└─ CHANGELOG.md
```

交付包不得包含生产密码、厂商密钥或现场访问令牌。

## 9. 下游签收门槛

- 三个接口契约测试全部通过；
- 能力目录非空、key 不重复；
- 当前 7 个天津 Action 在真实目录上全部编译通过；
- 同一 `consumeId` 重复提交 10 次，设备只动作一次；
- 同一 ID 携带不同请求时返回冲突；
- 上游重启后，原 `consumeId` 仍可查询；
- 同一机器人并发提交不会导致命令交错；
- 断网、超时和重启场景不会返回虚假成功；
- 海康 AGV 与华沿机械臂低速实机验证通过；
- 运维文档、版本记录和联调负责人齐全。

## 10. 不属于上游的内容

- 主 Action 和全局组合动作 CRUD；
- Action 版本、发布和不可变快照；
- Canvas 编辑器；
- 组合动作展开、循环/条件编译和 ExecutionPlan；
- 工作流异常策略、人工确认和业务许可；
- Action 页面权限；
- 为每个主 Action 新写分发代码。

以后接入新厂商时，上游新增相应 Adapter 和原子能力；下游 Action 配置模型不随厂商 SDK 改动。

## 11. 后续可选增强（P1）

- 原子命令取消接口；
- Webhook 或消息推送；
- 目录 ETag/增量同步；
- 机器人状态监控接口；
- 指标、链路追踪、TLS、身份认证和审计日志；
- 多实例部署下的分布式机器人执行租约。

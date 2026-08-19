# 上游原子 Action 接口需求

本文是给上游开发团队的接口契约。上游只提供当前原子能力目录和原子动作执行能力，不需要理解调度系统 Action 模块中的主 Action、组合动作、Canvas 或发布版本。

上游应提交的文件、设备资料和签收门槛见 [上游团队交付包与验收清单](upstream-delivery-checklist.md)。

## 一、当前原子能力目录

`GET /api/v1/atomic-capabilities`

```json
{
  "capabilities": [
    {
      "capabilityKey": "arm.move.linear",
      "inputSchema": {},
      "outputSchema": {},
      "resources": ["arm"],
      "sideEffect": "PHYSICAL",
      "retrySafety": "VERIFY_BEFORE_RETRY",
      "safetyCritical": true,
      "requiresMotionSafetyParameters": true
    }
  ]
}
```

约束：

- `capabilityKey` 在当前目录中唯一且稳定；
- 上游不维护原子能力版本号，也不计算 Schema Hash；
- 下游根据输入/输出 Schema、资源、副作用、重试安全等级和安全标记计算 `contractHash`；
- 下游发布 Action 时固定当时的 `contractHash`；当前目录契约变化后，旧 Action 必须重新编译发布；
- 删除能力时直接从当前目录移除，不复用其他能力的 key；
- 下游默认每 5 分钟同步一次，空目录被视为异常，不覆盖最后一次有效快照；
- `updatedAt`、ETag 或目录修订号可以作为同步优化提供，但不属于业务版本。

## 二、通过唯一消费 ID 提交原子 Action

`POST /api/v1/robots/{robotId}/atomic-actions`

```json
{
  "consumeId": "action-instance-id:node-ordinal",
  "workflowInstanceId": "optional",
  "workflowNodeInstanceId": "optional",
  "capabilityKey": "arm.move.linear",
  "input": {},
  "timeoutMs": 10000
}
```

`consumeId` 最大 128 字符，由下游根据 Action 实例和原子节点序号稳定生成。它同时承担唯一消费和去重语义，不再额外传递 `Idempotency-Key`。

上游必须保证：

- `consumeId` 建立数据库唯一约束，不能只保存在进程内存中；
- 相同 `consumeId`、相同请求体重复提交时，返回原执行记录，不再次驱动物理设备；
- 相同 `consumeId`、不同请求体返回 `409 Conflict`；
- 服务重启后仍保留消费记录和最终状态；
- 并发提交同一 `consumeId` 时只有一个请求能够创建并驱动命令；
- 同一 `robotId` 的物理命令不会交错执行，机器人忙时可以排队或明确拒绝；
- HTTP 应快速返回 `ACCEPTED`、当前状态或终态，不持续占用整个设备动作时长。

响应示例：

```json
{
  "consumeId": "action-instance-id:node-ordinal",
  "state": "ACCEPTED",
  "physicalResultKnown": false,
  "output": null,
  "evidence": {
    "deviceTaskId": "vendor-task-id"
  },
  "error": null
}
```

## 三、按消费 ID 查询状态

`GET /api/v1/atomic-actions/{consumeId}`

```json
{
  "consumeId": "action-instance-id:node-ordinal",
  "state": "SUCCEEDED",
  "physicalResultKnown": true,
  "output": {},
  "evidence": {
    "deviceTaskId": "vendor-task-id"
  },
  "error": null
}
```

允许状态：`ACCEPTED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`UNKNOWN`、`CANCELLED`。

状态语义：

- 终态不可回退；
- `physicalResultKnown` 必填；
- `SUCCEEDED` 必须同时满足 `physicalResultKnown=true`；
- 通信超时、进程重启或无法确认设备结果时返回 `UNKNOWN + false`；
- 只有确认设备已经停止且取消后的物理状态明确时，才能返回 `CANCELLED + true`；
- `error` 建议包含 `code`、`message`、`physicalResultKnown`、`retryable`、`deviceCode`、`handlingAdvice`；
- `evidence` 承载位置反馈、夹爪检测、图片地址、厂商任务号等可审计证据。

## 四、不再要求的接口和字段

一期不要求：

- `GET /api/v1/robots/{robotId}/availability`：提交接口的受理结果才是最终判断，预检查存在时序竞争；
- 上游原子能力 `version`；
- 上游提供 `schemaHash`；
- `capabilityVersion` 请求字段；
- `Idempotency-Key` 请求头；
- 独立 `commandId`。厂商命令号放入 `evidence.deviceTaskId`，状态统一按 `consumeId` 查询。

机器人在线状态若用于运维展示，可以作为可选监控接口提供，但不能作为 Action Runtime 的执行前置条件。

## 五、建议错误状态

| HTTP 状态 | 使用场景 |
|---:|---|
| 400 | 请求结构或基础字段错误 |
| 404 | robot、capabilityKey 或 consumeId 不存在 |
| 409 | consumeId 请求摘要冲突，或机器人忙且采用拒绝策略 |
| 422 | 参数越界、安全联锁或设备前置条件不满足 |
| 429 | 服务限流 |
| 503 | 厂商 SDK、设备或依赖不可用 |

POST 发生通信异常时，下游无法判断设备是否已经收到命令，会进入 `UNKNOWN_HOLD`。恢复时只能使用相同 `consumeId` 查询或重放，由上游返回原记录，禁止生成新的物理动作。

## 六、联调前需上游确认

1. 现有唯一消费 ID 是否具备数据库唯一约束、持久去重、重启保留和状态查询能力；
2. 现有接口与三个目标接口的字段映射；
3. 天津原子能力的最终输入/输出 Schema、单位、坐标系和范围；
4. 海康 AGV 与华沿机械臂的 `physicalResultKnown` 判定规则；
5. 超时后是否仍能通过 `consumeId` 查询最终结果；
6. 同一机器人命令串行由哪一层保证。

## 七、后续可选增强

- 取消接口；
- Webhook 或消息事件推送；
- 目录 ETag/增量同步；
- 机器人状态监控接口；
- 服务健康检查、指标和链路追踪；
- TLS、服务身份认证和审计日志。

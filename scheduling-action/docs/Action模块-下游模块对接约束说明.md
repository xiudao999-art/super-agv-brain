# Action 模块—下游模块对接约束说明

> 生效日期：2026-08-31  
> 目标线协议：2.0

## 1. 适用范围

本文定义 Action 与下游执行客户端之间长期有效的接口和安全约束。Action 内部页面、数据库实现以及 agvFlow 的流程模型不属于下游接口。

## 2. 不变量

1. Action 编排单个 Action 内的有序子动作；agvFlow 编排 Action 之间的流程。
2. 下游公开原子 operation，不公开固定主 Action 模板。
3. 下游不需要 actionKey、业务场景或流程节点上下文。
4. Action 不校验 operation 专属参数；下游在物理调用前完成整包参数预检。
5. Action 只向同一 actionInstanceId 发送一次 COMMAND。
6. 下游上报技术事实和设备事实，不生成 businessCode。
7. `physicalOutcome=UNKNOWN/PARTIALLY_COMPLETED` 时禁止自动重放。
8. Action 运行期间定义不可编辑；Action 进入任一终态后解除定义锁。

## 3. REGISTER 接口

operation 能力字段只有：

```text
operation
minTimeoutMs
maxTimeoutMs
```

会话级策略特性：

```text
RETRY_STEP
VERIFY_THEN_RETRY
SKIP_STEP
STOP_AND_REPORT
```

REGISTER 不定义参数 Schema。下游实现拥有参数规则，并以整包预检结果作为权威判断。

## 4. COMMAND 接口

COMMAND 顶层字段：

```text
version
messageType
messageId
sessionId
robotId
actionInstanceId
deviceCommandId
packageHash
input
timeoutMs
timestamp
```

`input.executionPlan.steps[]` 字段：

```text
stepId
operation
params
gate
onFailure
```

禁止添加：

- workflowInstanceId、workflowNodeInstanceId 或 nodeInstanceId；
- actionKey、revision、parameterSetId；
- Schema 或 schemaHash；
- businessCode、reasonCode、handlingConstraint；
- 人工等待、跨 Action 跳转、通用循环表达式。

`packageHash` 按规范化 `input` 计算 SHA-256。它用于核对实际包内容，不要求下游保存执行历史。

## 5. onFailure 接口

Action 定义中的规则为：

```text
reasonCode -> directive
```

Action 组包后转换为：

```text
CLIENT + clientCode -> directive
DEVICE + vendor + deviceType + code -> directive
```

下游规则的 policyId 由 Action 自动生成，格式固定为：

```text
<stepId>.rule.<从 1 开始的序号>
```

directive 字段按指令使用：

| 指令 | 允许字段 |
|---|---|
| STOP_AND_REPORT | action |
| SKIP_STEP | action |
| RETRY_STEP | action、maxRetries、delayMs、onExhaust |
| VERIFY_THEN_RETRY | action、maxRetries、delayMs、verify、onExhaust |

`onExhaust` 只允许 `STOP_AND_REPORT` 或 `SKIP_STEP`。`gate=true` 时所有最终路径均不得为 `SKIP_STEP`。

## 6. ACTION_EVENT 接口

身份字段必须与 COMMAND 一致：

```text
sessionId
robotId
actionInstanceId
deviceCommandId
```

状态：

```text
ACCEPTED
RUNNING
FINISHED
REJECTED
FAILED
UNKNOWN
```

物理结果：

```text
NOT_STARTED
CONFIRMED_SUCCEEDED
CONFIRMED_FAILED
PARTIALLY_COMPLETED
UNKNOWN
```

约束：

- FINISHED 必须是 CONFIRMED_SUCCEEDED；
- REJECTED 必须是 NOT_STARTED；
- FAILED 只允许 CONFIRMED_FAILED、PARTIALLY_COMPLETED 或 UNKNOWN；
- UNKNOWN 必须是 UNKNOWN；
- 非成功终态必须携带 error；
- 多步骤或部分完成终态必须携带 resolvedSteps；
- sequence 在 actionInstanceId 范围内严格递增。

## 7. error 接口

```json
{
  "clientCode": 50203,
  "message": "设备操作执行失败",
  "deviceFault": {
    "vendor": "HIKROBOT",
    "deviceType": "CHASSIS",
    "model": "Q7",
    "deviceId": "R01-CHASSIS",
    "code": "NAV_TIMEOUT",
    "message": "前方障碍持续存在"
  }
}
```

`clientCode` 是下游稳定技术码。`deviceFault` 仅在真实厂家设备提供原始故障时存在。`model` 和 `deviceId` 可用于 Action 详情诊断，但不进入默认映射键，也不进入 Action 向执行引擎交付的最小报告。

厂家映射默认精确键：

```text
vendor + deviceType + code
```

只有真实歧义时才增加 operation 条件。

## 8. 断线和重启

- TCP 重连只恢复通信和 REGISTER；
- 下游不查询、恢复或续跑旧 Action；
- Action 发送结果不确定、执行超时或连接中断时进入 UNKNOWN_HOLD；
- 同一 actionInstanceId 永不再次下发；
- 若现场确认可以重新执行，由 agvFlow 创建新的 actionInstanceId。

## 9. 变更规则

接口字段、枚举值或语义变更必须同时更新：

1. 本约束说明；
2. 联调清单及示例报文；
3. Java 协议测试；
4. C# 协议测试；
5. 双方模拟联调记录。

禁止通过旧 DTO、字段别名、双写、双读或静默默认值延长已删除接口。

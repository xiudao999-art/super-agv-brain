# Action 动态整包联调说明

> 文件名为历史路径，内容已于 2026-08-20 更新为当前动态整包方案，不再代表“固定四类动作包”。

## 运行链路

1. 页面维护当前 Action 的有序 `phases`、参数绑定和每步异常策略。
2. 页面选择设备联调参数集，填写少量本次业务入参。
3. `POST /api/action-executions/preview` 解析 `$input.*` 和 `$parameters.*`，返回最终 `MainAction`、`resolvedSteps` 和 `packageHash`。
4. 正式执行必须回传该 `packageHash`；预览后任何 Action 或参数变化都会导致后端拒绝执行。
5. 后端先持久化定义、参数、入参与命令快照，再通过 TCP 下发整包。
6. 下游按 `phases` 数组顺序执行；重复子动作使用不同 `phaseId`。

## 配置对象

- `ActionDefinition`：动作类型、有序步骤、Schema、绑定和超时。
- `ActionParameterSet`：机器人、工装、物料对应的位姿、速度、夹持力、视觉等细参数。
- `ActionExecution`：一次执行的完整不可变快照、事件和结果证据。

`revision` 是数据库并发控制号，不是 Action 业务版本。TCP 字段 `actionVersion=1.0` 是与 cnet8 的线协议兼容号，也不是 Action 版本。

## 异常与安全

每个 phase 可配置 `ABORT`、`RETRY_PHASE`、`VERIFY_BEFORE_RETRY`、`SKIP`，以及 `maxRetries`、`retryFromPhaseId`、`onExhaust`。Action 层只编码业务策略；下游原子级短重试由设备适配层内部消化。本轮不实现厂商错误码到业务异常码的映射。

- 开始执行后当前页面只读，即使执行进入终态也不自动解锁。
- 必须显式新建联调任务，才能基于当前 Action 再次调参。
- 断线、超时、Socket 写入结果不确定或下游无法确认物理结果时，进入 `UNKNOWN_HOLD`。
- `UNKNOWN_HOLD` 只允许查询和人工核对，迟到事件只补证据，不自动解锁或重放。

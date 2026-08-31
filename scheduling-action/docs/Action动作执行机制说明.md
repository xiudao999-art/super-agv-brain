# Action 动作执行机制说明

> 更新时间：2026-08-31

## 1. 模块定位

Action 是执行引擎和机器人下游之间的动作语义层。它拥有单个 Action 内的子动作编排，但不拥有跨 Action 的流程状态机。

```text
agvFlow
  保存流程节点与 actionDefinitionId
  生成 actionInstanceId
  根据最终报告决定下一流程节点
          |
          v
Action
  读取定义并校验在线能力
  编译失败策略并生成动作包
  创建执行记录后只下发一次
  保存事件并映射最终异常
          |
          v
下游
  整包预检
  串行执行步骤
  调用设备适配器
  上报物理事实和原始异常
```

## 2. Action 定义和执行颗粒度

一个 Action 定义是一个可独立执行的有序步骤包：

```text
id + name + enabled + timeoutMs + steps
```

每个步骤是：

```text
stepId + operation + params + gate + onFailure
```

固定业务场景通过不同 Action 定义表达。例如“抓取”可以由视觉检查、机械臂移动、夹爪闭合和负载确认组成。执行引擎只引用该定义 ID，不保存这些内部步骤。

步骤顺序变化只改变数组顺序；同一 operation 重复 N 次时，Action 展开成 N 个不同 stepId。下游通用执行器不增加主 Action 分支。

## 3. 编辑与运行互斥

编辑、停用、删除和执行都先对同一条 `action_definition` 记录加数据库写锁。

```text
编辑事务：锁定义行 -> 查询活动执行 -> 修改定义
执行事务：锁定义行 -> 查询 actionInstanceId -> 校验和组包 -> 创建执行记录
```

活动执行状态：

```text
DISPATCH_PENDING
DISPATCHED
ACCEPTED
RUNNING
```

终态：

```text
FINISHED
REJECTED
FAILED
UNKNOWN_HOLD
```

UNKNOWN_HOLD 会解除 Action 定义锁，因为原自动执行已经终止；它不会解除 agvFlow 的人工处置要求。

## 4. 执行入口

执行引擎只传三个字段：

```text
actionInstanceId
actionDefinitionId
robotId
```

Action 的执行顺序：

1. 校验三个标识；
2. 锁定并读取已启用定义；
3. 检查 actionInstanceId 是否已有记录；
4. 校验机器人在线、operation、超时和策略特性；
5. 编译失败规则；
6. 生成 commandInput 和 packageHash；
7. 在网络发送前创建执行记录；
8. 提交数据库事务，释放定义行锁；
9. 发送一条 COMMAND；
10. 保存发送回执或进入 UNKNOWN_HOLD。

相同 actionInstanceId 再次调用时只返回既有回执，不发送第二条 COMMAND。相同实例如果绑定了不同定义或机器人则直接报冲突。

## 5. 参数责任

Action 只检查 params 和 verifyParams 是 JSON 对象，不维护参数 Schema 或参数集。原因是 operation 的参数规则和真实设备能力都属于下游实现。

下游必须在任何物理调用前对整个动作包做预检。像 `pose.x=null`、数值越界或缺少必填字段应返回：

```text
state = REJECTED
physicalOutcome = NOT_STARTED
clientCode = 参数输入技术码
```

此类问题不是厂家设备原始异常。

## 6. 失败策略编译

厂家异常映射保存统一事实：

```text
vendor + deviceType + rawCode
  -> businessCode + reasonCode + handlingConstraint + message
```

Action 步骤保存当前业务场景的处置：

```text
reasonCode -> directive
```

组包时连接两者：

```text
CLIENT + clientCode -> directive
DEVICE + vendor + deviceType + rawCode -> directive
```

因此同一个厂家原始码在普通搬运和精密对接中可以使用不同策略，而下游不需要知道当前业务场景。

## 7. gate

`gate` 回答“当前步骤最终失败时，能否进入后续正常步骤”；`onFailure` 回答“失败后具体执行什么”。

- gate=true 禁止任何失败路径最终跳过；
- gate=false 也不会自动跳过，必须明确配置 SKIP_STEP；
- STOP_AND_REPORT 无论 gate 取值都停止整个动作包；
- UNKNOWN/PARTIALLY_COMPLETED 的物理安全约束高于 gate 和失败策略。

## 8. 执行证据

`action_execution.command_input_json` 保存实际下发 input。它与 packageHash 一起回答“本次到底向下游发送了什么”。Action 详情还保存：

- deviceCommandId 和协议版本；
- 下发会话、消息 ID；
- 最后事件会话、消息 ID 和 sequence；
- lastStepEvent；
- resolvedSteps；
- 原始 error JSON；
- 状态、物理结果和时间字段。

定义表后续发生编辑不会修改已经保存的 COMMAND 证据。

## 9. 最终报告

Action 向执行引擎只交付：

```text
actionInstanceId
result
physicalOutcome
failure
```

result 为：

```text
SUCCEEDED
FAILED
UNKNOWN_HOLD
```

failure 为：

```text
stepId
businessCode
handlingConstraint
message
deviceFault(vendor, deviceType, code, message)
```

执行引擎按 actionInstanceId 找回流程节点。它可以比 handlingConstraint 更保守，但不能突破 NON_RETRYABLE、CRITICAL 或未知物理结果等安全约束。

## 10. 人工介入闭环

当第三个流程节点的 Action 进入 UNKNOWN_HOLD：

1. Action 终止自动执行并交付 UNKNOWN_HOLD 报告；
2. agvFlow 按 actionInstanceId 定位第三个节点；
3. 流程进入人工任务，不执行第四个节点；
4. 现场人员核对机器人、机械臂、夹爪、物料和视觉证据；
5. agvFlow 保存操作者、时间、证据和结论；
6. 若确认未执行且允许重做，创建新的 actionInstanceId；
7. 若部分完成，先走人工恢复或补偿；
8. 若仍无法确认，继续保持人工挂起。

原 Action 记录不能通过直接改库伪造成成功。

## 11. 执行引擎后续接线

执行引擎只需：

1. 流程节点保存 actionDefinitionId；
2. 调用前生成并持久化 actionInstanceId；
3. 构造三字段 ExecuteActionCommand；
4. 删除参数集选择和 Action 内步骤保存；
5. 按 actionInstanceId 接收最终报告；
6. 使用 result、physicalOutcome 和 handlingConstraint 驱动现有流程决策。

Flowable 流程定义、节点顺序、人工任务、补偿、状态机核心规则和订单调度不需要因本次 Action 改造而重构。

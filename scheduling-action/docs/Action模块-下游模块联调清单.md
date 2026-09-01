# Action 模块—下游模块联调清单

> 文档状态：当前联调基线  
> 更新时间：2026-09-01
> 线协议：2.0  
> 适用模块：`scheduling-action` 与机器人下游执行客户端

## 1. 联调结论

Action 负责单个 Action 内的子动作编排、通用校验、策略编译、一次下发、执行记录和异常映射。下游只理解原子操作、固定参数、`gate`、执行指令及设备事实，不理解业务流程。

```text
agvFlow：Action 之间的流程顺序和流程决策
              |
              v
Action：单个 Action 内步骤编排、校验、组包、记录和异常解释
              |
              v
下游：整包预检、串行执行、设备适配和事实上报
```

当前契约不包含参数 Schema、参数集、`schemaHash`、Action revision 或上游流程上下文字段。Action 数据库保存实际下发的 `command_input_json`，用于还原本次真正发送的内容。

## 2. 责任边界

| 范围 | Action | 下游 |
|---|---|---|
| Action 定义 | 维护名称、超时、有序步骤、固定参数和失败策略 | 不保存主 Action 定义 |
| 能力 | 消费在线机器人注册信息并校验 | 注册实际支持的 operation、超时范围和策略特性 |
| 参数 | 只校验 `params` 是 JSON 对象 | 在物理调用前校验 operation 专属必填项、类型、范围和安全约束 |
| 失败策略 | 将 reasonCode 规则编译成 CLIENT/DEVICE 精确规则 | 机械执行重试、复核、跳过或停止 |
| `gate` | 校验门禁与跳过冲突 | 保证门禁步骤失败时不进入后续正常步骤 |
| 异常 | 映射客户端技术码和厂家原始码 | 上报稳定 clientCode 和原始 deviceFault |
| 物理结果 | 持久化并安全收敛 | 根据现场事实给出 physicalOutcome |
| 幂等 | 同一 actionInstanceId 最多发送一个 COMMAND | 不根据业务名称路由 |
| 流程 | 只交付最小最终报告 | 不决定人工任务、流程跳转或补偿 |

## 3. Action 定义

```json
{
  "id": "definition-001",
  "name": "移动到取料位",
  "enabled": true,
  "timeoutMs": 60000,
  "steps": [
    {
      "stepId": "move-to-pick",
      "operation": "MOVE_TO_MAP_POINT",
      "params": {
        "pointName": "PICK_A",
        "speed": 0.2
      },
      "gate": true,
      "onFailure": {
        "rules": [
          {
            "reasonCode": "MOVE.OBSTACLE_TIMEOUT",
            "directive": {
              "action": "RETRY_STEP",
              "maxRetries": 2,
              "delayMs": 1000,
              "onExhaust": "STOP_AND_REPORT"
            }
          }
        ],
        "defaultDirective": {
          "action": "STOP_AND_REPORT"
        }
      }
    }
  ]
}
```

约束：

- 新建定义由服务端生成 `id`，并固定为 `enabled=false`；
- `stepId` 在一个 Action 内唯一；
- 步骤数组顺序就是执行顺序；
- 重复执行同一种 operation 时展开为多个不同 `stepId`；
- `params` 必须是 JSON 对象，且不允许 `$parameters.*` 绑定；
- 失败规则只配置 `reasonCode + directive`；
- 下游规则的 `policyId` 由 Action 自动生成：`<stepId>.rule.<序号>`；
- `onFailure.defaultDirective` 必填；定义经编译后才转换为下游线协议的 `then/default`。

## 4. 能力注册

```json
{
  "version": "2.0",
  "messageType": "REGISTER",
  "messageId": "reg-001",
  "clientInstanceId": "client-001",
  "robotId": "R01",
  "robotType": "COMPOSITE",
  "operationCapabilities": [
    {
      "operation": "MOVE_TO_MAP_POINT",
      "minTimeoutMs": 1000,
      "maxTimeoutMs": 300000
    }
  ],
  "policyFeatures": [
    "RETRY_STEP",
    "VERIFY_THEN_RETRY",
    "SKIP_STEP",
    "STOP_AND_REPORT"
  ]
}
```

检查项：

- [ ] `operationCapabilities` 非空，operation 唯一；
- [ ] operation 使用稳定大写标识；
- [ ] `minTimeoutMs <= maxTimeoutMs`；
- [ ] 不发送或要求 `schemaHash`；
- [ ] `policyFeatures` 只使用双方约定的四种指令；
- [ ] 重连后重新 REGISTER，但不恢复旧动作。

## 5. COMMAND

```json
{
  "version": "2.0",
  "messageType": "COMMAND",
  "messageId": "msg-001",
  "sessionId": "session-001",
  "robotId": "R01",
  "actionInstanceId": "action-001",
  "deviceCommandId": "dc-001",
  "packageHash": "e3b0...",
  "input": {
    "executionPlan": {
      "steps": [
        {
          "stepId": "move-to-pick",
          "operation": "MOVE_TO_MAP_POINT",
          "params": {
            "pointName": "PICK_A",
            "speed": 0.2
          },
          "gate": true,
          "onFailure": {
            "rules": [
              {
                "policyId": "move-to-pick.rule.1",
                "when": {
                  "source": "DEVICE",
                  "vendor": "HIKROBOT",
                  "deviceType": "CHASSIS",
                  "code": "NAV_TIMEOUT"
                },
                "then": {
                  "action": "RETRY_STEP",
                  "maxRetries": 2,
                  "delayMs": 1000,
                  "onExhaust": "STOP_AND_REPORT"
                }
              }
            ],
            "default": {
              "action": "STOP_AND_REPORT"
            }
          }
        }
      ]
    }
  },
  "timeoutMs": 60000,
  "timestamp": "2026-08-31T06:00:00Z"
}
```

检查项：

- [ ] 不包含 workflowInstanceId、nodeInstanceId、actionKey、parameterSetId 或 revision；
- [ ] 不包含 `configSnapshot`；
- [ ] `packageHash` 是规范化 `input` 的 SHA-256；
- [ ] `stepId` 非空且包内唯一；
- [ ] `operation` 已注册；
- [ ] `params` 是对象；
- [ ] `gate` 是布尔值；
- [ ] 每个 `onFailure` 有 rules 数组和 default；
- [ ] CLIENT 规则只按 clientCode 匹配；
- [ ] DEVICE 规则只按 vendor、deviceType、code 精确匹配。

## 6. 下游整包预检

下游收到 COMMAND 后，必须在第一条物理指令发出前完成：

1. 验证消息身份、执行槽和基本字段；
2. 验证所有 stepId 唯一；
3. 验证全部 operation 已注册；
4. 验证全部 operation 专属参数、类型、范围和安全边界；
5. 验证全部 gate 和 onFailure；
6. 验证复核 operation 及其参数；
7. 验证总超时可执行；
8. 全部通过后才发送 ACCEPTED 并开始物理执行。

例如 `pose.x=null` 必须返回 `REJECTED + NOT_STARTED`，不能执行到该步骤才报错，也不能伪造成厂家设备故障。

## 7. gate 和失败指令

| 配置 | 结果 |
|---|---|
| gate=true，重试后成功 | 进入下一步骤 |
| gate=true，任一最终路径是 SKIP_STEP | 整包非法，预检拒绝 |
| gate=true，耗尽后 STOP_AND_REPORT | 停止并上报 |
| gate=false，明确 SKIP_STEP | 记录跳过并继续 |
| gate=false，STOP_AND_REPORT | 停止并上报 |

重试或跳过不得突破物理安全事实。`UNKNOWN` 或 `PARTIALLY_COMPLETED` 时必须停止自动执行。

## 8. ACTION_EVENT

```json
{
  "version": "2.0",
  "messageType": "ACTION_EVENT",
  "messageId": "event-008",
  "sessionId": "session-001",
  "robotId": "R01",
  "actionInstanceId": "action-001",
  "deviceCommandId": "dc-001",
  "sequence": 8,
  "state": "FAILED",
  "stepEvent": {
    "eventType": "STEP_FAILED",
    "stepId": "move-to-pick",
    "operation": "MOVE_TO_MAP_POINT",
    "attempt": 3,
    "policyId": "move-to-pick.rule.1"
  },
  "physicalOutcome": "CONFIRMED_FAILED",
  "error": {
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
  },
  "timestamp": "2026-08-31T06:00:10Z"
}
```

终态为 `FINISHED`、`REJECTED`、`FAILED` 或 `UNKNOWN`。Action 将下游 `UNKNOWN` 收敛为本地 `UNKNOWN_HOLD`。终态到达后 Action 定义解除编辑锁，但 `UNKNOWN_HOLD` 的业务流程仍必须等待人工核对。

## 9. 联调验收清单

### Action

- [ ] 定义接口全部按 id 操作；
- [ ] 新建默认未启用；
- [ ] 启用和执行前校验在线机器人能力；
- [ ] 编辑、停用、删除和执行锁定同一条定义记录；
- [ ] 同一 actionInstanceId 最多发送一次 COMMAND；
- [ ] 实际 COMMAND input 和 packageHash 一致；
- [ ] 发送结果不确定进入 UNKNOWN_HOLD；
- [ ] 保存原始事件和最小最终报告。

### 下游

- [ ] REGISTER 不含 schemaHash；
- [ ] 不依赖主 Action 名称或业务流程上下文；
- [ ] 任意步骤换序或重复时仍按数组机械执行；
- [ ] 全包预检发生在任何物理调用之前；
- [ ] 正确执行四种失败指令和 gate；
- [ ] 厂家原始码、原始消息不丢失；
- [ ] 最终事件发送后释放执行槽；
- [ ] 断线重连不恢复或重放旧动作。

### 共同验收场景

- [ ] 两步顺序交换；
- [ ] 同一 operation 重复三次且 stepId 不同；
- [ ] 参数缺失返回 REJECTED + NOT_STARTED；
- [ ] CLIENT 技术错误命中策略；
- [ ] DEVICE 厂家错误命中策略；
- [ ] gate=true 与 SKIP_STEP 冲突被拒绝；
- [ ] 包内重试成功和重试耗尽；
- [ ] TCP 写入结果不确定；
- [ ] UNKNOWN/PARTIALLY_COMPLETED 不自动重放；
- [ ] Action 最终报告被执行引擎按 actionInstanceId 接收。

## 10. Bug 归属

| 现象 | 首要归属 |
|---|---|
| Action 保存重复 stepId 或非法 gate 策略 | Action |
| 未注册 operation 仍然下发 | Action |
| 参数类型错误未在物理调用前拒绝 | 下游 |
| 下游根据 Action 名称增加业务分支 | 下游 |
| 同一 actionInstanceId 发送多次 COMMAND | Action |
| 厂家原始码或消息丢失 | 下游 |
| 原始码映射为错误 businessCode | Action |
| UNKNOWN 后自动重放物理动作 | 触发重放的一方 |
| 流程节点无法按 actionInstanceId 找回 | 执行引擎 |

真实设备验收必须与“代码编译通过”和“模拟客户端通过”分别记录。

## 11. cnet8 当前快照兼容边界

Action 保留本清单定义的规范 2.0 模型，在 `RobotActionTransport` 的 TCP 边界增加
`CNET8_V2` 方言适配。兼容只改变线上的信封和字段结构，不修改 Action 定义、执行记录、
业务 `packageHash` 或本地状态机。

### 11.1 会话与注册

- 首条 REGISTER 按字段形状精确选择 `ACTION_V2` 或 `CNET8_V2`，一个连接内禁止切换和混用；
- cnet8 使用 `REGISTER -> REGISTER_ACK -> RegisterRobot` 两阶段注册；
- 收到 `RegisterRobot.Capabilities` 前，会话不进入在线能力列表，也不能下发 COMMAND；
- cnet8 未提供超时范围和策略特性时，Action 使用明确的 cnet8 当前实现档案：
  `timeoutMs=1..3600000`，指令集合为四种标准失败指令；
- PING/PONG 的 `sessionId` 和 `sequence` 从 `MessageInfo` 中读取并严格校验。

### 11.2 COMMAND 转换

- Action `input.executionPlan.steps` 转成 cnet8 `MessageInfo.Steps`；
- `params` 只重命名为 `Parameters`，内容保持不透明 JSON，Action 不复制设备 SDK 参数转换；
- `onFailure.rules` 转成 cnet8 的平铺 `OnFailure`，Action 的 default 编译成全通配规则；
- Action 原始 `packageHash` 保持不变；cnet8 信封中的 `PackageHash` 按其当前
  `ExecutionPlanHash.Compute` 字段集合另行计算；
- 同一 Action 的重复失败选择器和最坏退避预算在发送前按 cnet8 边界校验。

当前不能等价表达的策略必须在 Action 下发前拒绝，禁止静默删除或改变含义：

- CLIENT 失败规则；
- `RETRY_STEP/VERIFY_THEN_RETRY` 耗尽后的 `SKIP_STEP`；
- `maxRetries > 3` 或 `backoffMs > 300000`。

### 11.3 ACTION_EVENT 转换

- cnet8 PascalCase、`MessageInfo` 和字符串 `ClientCode` 转成 Action 规范事件；
- cnet8 未提供事件 sequence，Action 按当前 TCP 会话接收顺序生成正整数序号；
- `DeviceFault.RawCode/RawMessage` 转成规范 `code/message`，厂家原始内容不丢失；
- 字符串技术码使用显式目录映射；未知字符串保存在 `rawClientCode`，并归入未映射人工处理；
- 原始 `MessageInfo` 保存在规范 error 的 `rawMessageInfo` 中用于审计；
- `UNKNOWN_HOLD + UNKNOWN` 转成规范 `UNKNOWN`；
  `UNKNOWN_HOLD + PARTIALLY_COMPLETED` 转成 `FAILED + PARTIALLY_COMPLETED`，
  由 Action 本地状态机安全收敛为 `UNKNOWN_HOLD`。

### 11.4 仍由下游闭环的事项

- 原子命令 `commandId` 在包内重试时的生成和复用语义；
- `UNKNOWN_HOLD` 经操作员核实后的生产解除入口、确认响应和审计；
- 厂家原始码是否区分大小写的精确匹配约束；
- 如果业务必须使用 CLIENT 规则或重试耗尽跳过，下游需要先补充可等价执行的协议能力。

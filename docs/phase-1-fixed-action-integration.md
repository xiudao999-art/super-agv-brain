# 一期固定动作包联调说明

## 1. 范围

一期只打通下游调用上游既有主 Action 的核心链路：

| Action | 下游模板版本 | 上游 Action 版本 | phase 数 |
|---|---:|---:|---:|
| `MOVE` | `1.0.0` | `1.0` | 1 |
| `ARM.HOME` | `1.0.0` | `1.0` | 3 |
| `ARM.PICK` | `1.0.0` | `1.0` | 8 |
| `ARM.PLACE` | `1.0.0` | `1.0` | 8 |

模板位于 `scheduling-action/src/main/resources/fixed-action-packages`。调用方不能提交或替换 `phases`，只能提供各动作允许的业务参数。

一期明确不包含：动态 Action 配置执行、任意并行、脚本、动作包下发后的主动取消、权限，以及真实设备验收。

## 2. 端口与模块

- HTTP API：`8081`，直接维护在主应用 `application.yml`；
- Robot TCP Bridge：`8080`，一期默认值维护在 `ActionModuleDefaults`；
- MySQL：默认数据库 `kunling_action`，Flyway V5 新增动作包执行与事件审计表。

数据源地址、用户名和密码直接维护在主应用 `application.yml`，不使用项目自定义环境变量占位。应用启动后 Flyway 会迁移该文件指向的数据库，部署前仍应核对目标地址。

Robot Bridge 位于 `scheduling-action` 内部，只处理 `REGISTER`、心跳、`COMMAND`、`ACTION_EVENT` 和 `QUERY_ACTION` 等线协议；固定动作应用服务负责模板、幂等指纹、数据库事务与 `UNKNOWN_HOLD` 状态机。

## 3. HTTP 调用

`POST /api/v1/robot-action-executions`

```json
{
  "actionInstanceId": "workflow-100-node-20-attempt-1",
  "robotId": "ROBOT-01",
  "actionType": "MOVE",
  "workflowInstanceId": "workflow-100",
  "workflowNodeInstanceId": "node-20",
  "input": {
    "pointName": "P01",
    "speed": 0.5,
    "pose": {"x": 12480, "y": 8220, "yaw": 90, "map": "LAB"},
    "arrival": {
      "positionToleranceMm": 5,
      "angleToleranceDeg": 5,
      "timeoutMs": 30000
    }
  }
}
```

查询本地下游状态：

```text
GET /api/v1/robot-action-executions/{actionInstanceId}
```

请求上游核对状态，不会重放动作：

```text
POST /api/v1/robot-action-executions/{actionInstanceId}/query
```

查看当前已注册机器人及本会话接受的 Action：

```text
GET /api/v1/robots
```

一期不提供取消接口。旧 `/api/action-executions` 动态执行入口默认关闭。

## 4. 幂等与安全

1. 下游先保存最终 `commandInput`、`packageHash`、请求指纹和稳定 `deviceCommandId`，事务成功后才写 TCP；
2. 相同 `actionInstanceId` 和相同请求重复提交时只返回已有记录，不再发送 `COMMAND`；
3. 相同 `actionInstanceId` 对应不同动作包或工作流上下文时返回 `409 Conflict`；
4. TCP 写入结果不确定、机器人执行中断线、上游返回 `UNKNOWN`、失败事件无法证明物理结果时进入 `UNKNOWN_HOLD`；
5. HOLD 后迟到事件只补充证据，不自动解除 HOLD，不自动推进工作流；
6. 重连后只对 HOLD 记录发送 `QUERY_ACTION`，绝不自动重放原动作包；
7. 服务重启时，所有未终结动作进入 `UNKNOWN_HOLD`。

## 5. 模拟验收口径

一期模拟联调至少验证：四类模板完整下发并接收终态、Hash 固定为 64 位 SHA-256、重复实例不二次下发、指纹冲突返回 409、查询消息为 `QUERY_ACTION`、断线和未知结果进入 `UNKNOWN_HOLD`。

模拟通过只说明下游协议、持久化和状态机闭环完成。海康 AGV、华沿机械臂的真实坐标、联锁、到位判定和物理结果确定性仍须现场验收。

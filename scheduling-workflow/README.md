# scheduling-workflow

独立的Flowable工作流程模块。bpmn.js导出BPMN 2.0 XML后调用部署接口。

主要接口：

- `POST /api/workflows/definitions/deploy`：部署XML。
- `GET /api/workflows/definitions`：查询最新流程定义。
- `GET /api/workflows/definitions/{id}/xml`：重新获取XML供bpmn.js编辑。
- `POST /api/workflows/instances`：启动实例。
- `POST /api/workflows/instances/{id}/suspend`：中断。
- `POST /api/workflows/instances/{id}/activate`：恢复。
- `GET /api/workflows/instances/{id}/active-nodes`：当前运行节点。
- `POST /api/workflows/executions/{executionId}/trigger`：推进等待节点。
- `GET /api/workflows/instances/{id}/history`：历史节点。
- `GET /api/workflows/tasks`：人工异常任务。
- `POST /api/workflows/tasks/{taskId}/claim`：签收人工任务。
- `POST /api/workflows/tasks/{taskId}/complete`：完成人工任务。

启动实例请求：

```json
{
  "processDefinitionKey": "agvManualRecovery",
  "businessKey": "ORDER-20260822-001",
  "variables": {"robotId": "AGV-001"}
}
```

触发等待节点：

```json
{"variables": {"success": false, "errorCode": "AGV_BLOCKED"}}
```

人工处理重试：

```json
{"variables": {"decision": "RETRY"}}
```

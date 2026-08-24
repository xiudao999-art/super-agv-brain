package com.kunling.scheduling.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.workflow.action.WorkFlowActionGateway;
import com.kunling.scheduling.workflow.action.WorkFlowExecutionsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ExecuteService {
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private WorkFlowActionGateway workFlowActionGateway;

    @Resource
    private WorkFlowExecutionsGateway workFlowExecutionsGateway;


    public void executeTask(String actionKey, String flowId, String nodeId) {
        List<ActionParameterSetView> actions = workFlowActionGateway.actions(actionKey);
        log.info("流程节点----{}--开始进行", actionKey);
        ObjectNode input = objectMapper.createObjectNode();
        input.put("targetPoint", "PICK_STATION_A");
        ExecuteActionCommand command = new ExecuteActionCommand(
                flowId,                                      // workflowInstanceId
                nodeId,                                      // workflowNodeInstanceId
                UUID.randomUUID().toString(),                                // actionInstanceId
                "R01",                                    // 实际注册的robotId
                actionKey,
                actions.get(0).id()   // MOVE参数集ID
        );
        workFlowExecutionsGateway.execute(command);
    }
}

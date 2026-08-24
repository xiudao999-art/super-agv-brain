package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.workflow.dto.FlowSuccessCallbackRequest;
import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import com.kunling.scheduling.workflow.service.FlowControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/flow")
@Tag(name = "AGV流程回调")
public class FlowController {
    private final FlowControlService flowControlService;

    public FlowController(FlowControlService flowControlService) {
        this.flowControlService = flowControlService;
    }

    @PostMapping("/callbacks/success")
    @Operation(summary = "AGV动作成功回调并推进到下一流程节点")
    public Map<String, Boolean> success(@Valid @RequestBody FlowSuccessCallbackRequest request) {
        FlowStartRequest callback = new FlowStartRequest();
        callback.setExecutionId(request.getExecutionId());
        try {
            callback.setFlowId(Long.valueOf(request.getFlowId()));
            callback.setBusinessKey(Long.valueOf(request.getBusinessKey()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("flowId和businessKey必须是整数", exception);
        }
        boolean handled = flowControlService.processCallback(callback);
        return Collections.singletonMap("success", handled);
    }
}

package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.workflow.dto.ExceptionCallBackDto;
import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.FlowService;
import com.kunling.scheduling.workflow.service.NodeStateTransitionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/flow")
@Tag(name = "AGV流程回调")
public class FlowController {
    @Resource
    private FlowService flowService;




//    @PostMapping("/callbacks/success")
//    @Operation(summary = "AGV动作成功回调并推进到下一流程节点")
//    public Map<String, Boolean> success(@Valid @RequestBody FlowSuccessCallbackRequest request) {
//        FlowStartRequest callback = new FlowStartRequest();
//        callback.setExecutionId(request.getExecutionId());
//        try {
//            callback.setFlowId(Long.valueOf(request.getFlowId()));
//            callback.setBusinessKey(Long.valueOf(request.getBusinessKey()));
//        } catch (NumberFormatException exception) {
//            throw new IllegalArgumentException("flowId和businessKey必须是整数", exception);
//        }
//        boolean handled = flowControlService.processCallback(callback);
//        return Collections.singletonMap("success", handled);
//    }

//    @PostMapping("/callbacks/failure")
//    @Operation(summary = "AGV动作失败回调并终止当前任务流程")
//    public Map<String, Boolean> failure(@Valid @RequestBody FlowFailureCallbackRequest request) {
//        return Collections.singletonMap("success", flowControlService.processFailure(request));
//    }

    //异常流程外部回调处理
    @PostMapping("/callbacks/failure")
    @Operation(summary = "AGV动作失败回调并终止当前任务流程",description = "当前支持两种异常恢复, 1:SUCCEEDED 2:SKIPPED")
    public void dealExceptionCallBack(@RequestBody ExceptionCallBackDto dto) {
        //todo  SUCCEEDED认为当前节点修复成功,继续运行   SKIPPED认为当前节点不在进行,跳过执行后续执行任务
        flowService.dealExceptionCallBack(dto);
    }
}

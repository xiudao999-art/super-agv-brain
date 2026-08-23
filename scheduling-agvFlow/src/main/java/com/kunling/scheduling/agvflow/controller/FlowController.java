//package com.kunling.scheduling.agvflow.controller;
//
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.kunling.scheduling.agvflow.domain.dto.*;
//
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import javax.annotation.Resource;
//import javax.validation.Valid;
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/flows")
//@Tag(name = "流程管理")
//public class FlowController {
//
//    @Resource
//    private FlowService flowService;
//
//    @PostMapping
//    @Operation(summary = "新建流程")
//    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody FlowCreateRequest request) {
//        HashMap<String, Long> response = new HashMap<>();
//        Long flowId = flowService.createFlow(request);
//        response.put("id",flowId);
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }
//
//    @GetMapping("/{id}")
//    @Operation(summary = "查询流程详情")
//    public FlowDetail getDetail(@PathVariable Long id) {
//        return flowService.getFlowDetail(id);
//    }
//
//    @GetMapping
//    @Operation(summary = "分页查询流程列表")
//    public Page<FlowListItem> pageList(
//            @RequestParam(defaultValue = "1") int current,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(required = false) String keyword) {
//        return flowService.pageFlows(current, size, keyword);
//    }
//
//    @PutMapping("/{id}")
//    @Operation(summary = "编辑流程")
//    public FlowDetail update(@PathVariable Long id,
//                             @Valid @RequestBody FlowUpdateRequest request) {
//        return flowService.updateFlow(id, request);
//    }
//
//    @PatchMapping("/{id}/progress")
//    @Operation(summary = "更新订单流程的当前节点和状态")
//    public FlowDetail updateProgress(@PathVariable Long id,
//                                     @Valid @RequestBody FlowProgressUpdateRequest request) {
//        return flowService.updateProgress(id, request);
//    }
//
//    @DeleteMapping("/{id}")
//    @Operation(summary = "删除流程")
//    public ResponseEntity<Void> delete(@PathVariable Long id) {
//        flowService.deleteFlow(id);
//        return ResponseEntity.noContent().build();
//    }
//}

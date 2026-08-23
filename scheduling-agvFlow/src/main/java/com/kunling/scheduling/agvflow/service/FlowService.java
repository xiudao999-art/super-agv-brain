//package com.kunling.scheduling.agvflow.service;
//
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.baomidou.mybatisplus.extension.service.IService;
//import com.kunling.scheduling.agvflow.domain.dto.FlowCreateRequest;
//import com.kunling.scheduling.agvflow.domain.dto.FlowDetail;
//import com.kunling.scheduling.agvflow.domain.dto.FlowListItem;
//import com.kunling.scheduling.agvflow.domain.dto.FlowUpdateRequest;
//import com.kunling.scheduling.agvflow.domain.dto.FlowProgressUpdateRequest;
//import com.kunling.scheduling.agvflow.domain.entity.Flow;
//
//public interface FlowService extends IService<Flow> {
//
//    Long createFlow(FlowCreateRequest request);
//
//    FlowDetail getFlowDetail(Long id);
//
//    Page<FlowListItem> pageFlows(int current, int size, String keyword);
//
//    FlowDetail updateFlow(Long id, FlowUpdateRequest request);
//
//    FlowDetail updateProgress(Long id, FlowProgressUpdateRequest request);
//
//    void deleteFlow(Long id);
//}

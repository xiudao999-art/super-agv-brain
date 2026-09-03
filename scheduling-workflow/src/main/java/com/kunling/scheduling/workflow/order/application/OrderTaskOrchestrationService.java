package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.resp.TaskInfoResp;
import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.FlowNodeService;
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderTaskOrchestrationService {
    private final CustomerOrderMapper orderMapper;
    private final OrderTaskMapper taskMapper;
    private final FlowTemplateMapper flowTemplateMapper;
    private final WorkflowTemplateMapper workflowTemplateMapper;
    private final FlowControlService flowControlService;
    private final ApplicationEventPublisher publisher;
    private final OrderSyncLockService lockService;

    private static final String KEY_PREFIX = "order:task:";

    public OrderTaskOrchestrationService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper,
                                         FlowTemplateMapper flowTemplateMapper,
                                         WorkflowTemplateMapper workflowTemplateMapper,
                                         FlowControlService flowControlService,
                                         ApplicationEventPublisher publisher, OrderSyncLockService lockService) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
        this.flowTemplateMapper = flowTemplateMapper;
        this.workflowTemplateMapper = workflowTemplateMapper;
        this.flowControlService = flowControlService;
        this.publisher = publisher;
        this.lockService = lockService;
    }

//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
//    public void onTaskFlowStatus(TaskFlowStatusEvent event) {
//        if (event.getType() == TaskFlowStatusEvent.Type.SUCCEEDED) {
//            completeAndStartNext(event.getTaskId());
//        } else if (event.getType() == TaskFlowStatusEvent.Type.WAITING) {
//            markWaiting(event.getTaskId(), event.getCurrentStep(), event.getMessage());
//        } else {
//            markFailed(event.getTaskId(), event.getMessage());
//        }
//    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderExecutionReleased(OrderExecutionReleasedEvent event) {
        lockService.unlockOrderExecution(event.getOrderId());
    }

    /**
     * 定时调度入口：先推进运行中的订单，否则按订单优先级选择一个排队订单。
     */
    public boolean dispatchNext() {
        String key = KEY_PREFIX + "start";
        boolean locked = lockService.tryLock(key, "1", Duration.ofSeconds(10));
        if (!locked) return false;
        try {
            List<CustomerOrder> runningList = orderMapper.selectList(Wrappers.<CustomerOrder>lambdaQuery()
                    .in(CustomerOrder::getStatus, OrderStatus.RUNNING, OrderStatus.FAILED, OrderStatus.WAITING)
                    .last("limit 1"));
            if (!CollectionUtils.isEmpty(runningList)) {
                //获取进行中的代码
                List<CustomerOrder> runningOrder = runningList.stream()
                        .filter(order -> order.getStatus() == OrderStatus.RUNNING)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(runningOrder)){
                    long activeTasks = taskMapper.selectCount(Wrappers.<OrderTask>lambdaQuery()
                            .eq(OrderTask::getOrderId, runningList.get(0).getId())
                            .in(OrderTask::getStatus, OrderTaskStatus.RUNNING, OrderTaskStatus.FAILED));
                    if (activeTasks > 0) {
                        return false;
                    }
                    return activeTasks == 0 && startFirstQueued(runningOrder.get(0).getId());
                }
                List<CustomerOrder> failedOrder = runningList.stream()
                        .filter(order -> order.getStatus() == OrderStatus.FAILED)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(failedOrder)) {
                    return false;
                }
            }

            CustomerOrder queued = orderMapper.selectOne(Wrappers.<CustomerOrder>lambdaQuery()
                    .eq(CustomerOrder::getStatus, OrderStatus.QUEUED)
                    .orderByDesc(CustomerOrder::getPriority)
                    .orderByAsc(CustomerOrder::getIssuedAt)
                    .orderByAsc(CustomerOrder::getId)
                    .last("limit 1"));
            return queued != null && startFirstQueued(queued.getId());
        }finally {
            lockService.unlock(key, "1");
        }


    }

    public boolean startFirstQueued(Long orderId) {
        OrderTask first = taskMapper.selectOne(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, orderId)
                .eq(OrderTask::getStatus, OrderTaskStatus.QUEUED)
                .orderByAsc(OrderTask::getTaskSeq).last("limit 1"));
        if (first != null) {
            return startTask(first);
        }
        return false;
    }

    public boolean startTask(OrderTask task) {
        try {
            FlowTemplate flowTemplate = flowTemplateMapper.selectOne(Wrappers.<FlowTemplate>lambdaQuery()
                    .eq(FlowTemplate::getId, task.getFlowTemplateId())
                    .eq(FlowTemplate::getStatus, 1));
            if (flowTemplate == null) {
                log.error("流程编号不存在或未启用: {}", task.getFlowTemplateId());
                throw new NoSuchElementException("流程编号不存在或未启用: " + task.getFlowTemplateId());
            }
            WorkflowTemplateEntity workflowTemplate = workflowTemplateMapper.selectById(flowTemplate.getSourceTemplateId());
            if (workflowTemplate == null || StringUtils.isBlank(workflowTemplate.getProcessDefinitionId())) {
                log.error("流程尚未部署: {}", task.getFlowTemplateId());
                throw new IllegalStateException("流程尚未部署: " + task.getFlowTemplateId());
            }

            FlowStartRequest request = new FlowStartRequest();
            request.setProcessDefinitionId(workflowTemplate.getProcessDefinitionId());
            request.setBusinessKey(task.getOrderId());
            request.setTaskId(task.getId());
            request.setTemplateId(flowTemplate.getId());
            boolean started = flowControlService.start(request);
            if (!started){
                log.error("流程启动或首节点下发失败: {}", task.getId());
                throw new IllegalStateException("流程启动或首节点下发失败: " + task.getId());
            }
            // FlowControlService.start已在同一张order_task表中保存流程实例和运行状态。
            // 启动流程后重新查询任务，避免使用启动前的旧数据覆盖最新执行结果。
            //
            OrderTask startedTask = taskMapper.selectById(request.getTaskId());
            if (startedTask != null && startedTask.getStatus() == OrderTaskStatus.RUNNING){
                updateOrderRunning(task.getOrderId());
            }
            return true;
        } catch (RuntimeException exception) {
            markFailed(task.getId(), exception.getMessage());
            return false;
        }
    }

    @Transactional
    public void completeAndStartNext(Long taskId) {
        OrderTask task = requireTask(taskId);
        if (task.getStatus() != OrderTaskStatus.SUCCEEDED) {
            task.setStatus(OrderTaskStatus.SUCCEEDED);
            task.setCompletedAt(LocalDateTime.now());
            task.setErrorCode(null);
            task.setErrorMessage(null);
            taskMapper.updateById(task);
        }
        refreshOrderProgress(task.getOrderId());
        OrderTask next = taskMapper.selectOne(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, task.getOrderId())
                .eq(OrderTask::getStatus, OrderTaskStatus.QUEUED)
                .orderByAsc(OrderTask::getTaskSeq).last("limit 1"));
        if (next == null) {
            publisher.publishEvent(new OrderExecutionReleasedEvent(task.getOrderId()));
        }
    }

    @Transactional
    public void markWaiting(Long taskId, String step, String message) {
        OrderTask task = requireTask(taskId);
        task.setStatus(OrderTaskStatus.QUEUED);
        task.setErrorCode("DISPATCH_WAITING");
        task.setErrorMessage(message);
        taskMapper.updateById(task);
    }

    @Transactional
    public void markFailed(Long taskId, String message) {
        OrderTask task = requireTask(taskId);
        task.setStatus(OrderTaskStatus.FAILED);
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorCode("TASK_EXECUTION_FAILED");
        task.setErrorMessage(StringUtils.defaultIfBlank(message, "任务执行失败"));
        taskMapper.updateById(task);
        CustomerOrder order = orderMapper.selectById(task.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatus.FAILED);
            order.setErrorCode(task.getErrorCode());
            order.setErrorMessage(task.getErrorMessage());
            orderMapper.updateById(order);
//            publisher.publishEvent(new OrderExecutionReleasedEvent(task.getOrderId()));
        }
    }

    private boolean hasOtherRunningOrder(Long orderId) {
        return orderMapper.selectCount(Wrappers.<CustomerOrder>lambdaQuery()
                .eq(CustomerOrder::getStatus, OrderStatus.RUNNING)
                .ne(CustomerOrder::getId, orderId)) > 0;
    }

    private void updateOrderRunning(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        order.setStatus(OrderStatus.RUNNING);
        order.setErrorCode(null);
        order.setErrorMessage(null);
        orderMapper.updateById(order);
    }

    private void refreshOrderProgress(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        int total = Math.toIntExact(taskMapper.selectCount(Wrappers.<OrderTask>lambdaQuery().eq(OrderTask::getOrderId, orderId)));
        int completed = Math.toIntExact(taskMapper.selectCount(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, orderId).eq(OrderTask::getStatus, OrderTaskStatus.SUCCEEDED)));
        order.setStatus(total > 0 && completed == total ? OrderStatus.SUCCEEDED : OrderStatus.RUNNING);
        orderMapper.updateById(order);
    }

    private CustomerOrder requireOrder(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) throw new NoSuchElementException("订单不存在: " + id);
        return order;
    }

    private OrderTask requireTask(Long id) {
        OrderTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new NoSuchElementException("订单任务不存在: " + id);
        }
        return task;
    }


}

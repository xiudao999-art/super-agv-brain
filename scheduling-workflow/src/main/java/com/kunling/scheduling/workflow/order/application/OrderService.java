package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import com.kunling.scheduling.workflow.order.api.OrderResponses;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskCount;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final CustomerOrderMapper orderMapper;
    private final OrderTaskMapper taskMapper;
    private final FlowTemplateMapper flowTemplateMapper;
    private final WorkflowTemplateMapper workflowTemplateMapper;
    private final FlowNodeMapper flowNodeMapper;

    public OrderService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper,
                        FlowTemplateMapper flowTemplateMapper, WorkflowTemplateMapper workflowTemplateMapper,
                        FlowNodeMapper flowNodeMapper) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
        this.flowTemplateMapper = flowTemplateMapper;
        this.workflowTemplateMapper = workflowTemplateMapper;
        this.flowNodeMapper = flowNodeMapper;
    }

    public OrderResponses.Page page(long pageNum, long pageSize, OrderStatus status, String source, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("分页参数不合法");
        String search = StringUtils.trimToNull(keyword);
        Page<CustomerOrder> result = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<CustomerOrder>lambdaQuery()
                        .eq(status != null, CustomerOrder::getStatus, status)
                        .eq(StringUtils.isNotBlank(source), CustomerOrder::getSource, StringUtils.trim(source))
                        .and(search != null, q -> q.like(CustomerOrder::getUpstreamOrderNo, search)
                                .or().like(CustomerOrder::getSystemOrderNo, search))
                        .orderByDesc(CustomerOrder::getIssuedAt).orderByDesc(CustomerOrder::getId));
        Map<Long, OrderTaskCount> counts = taskCounts(result.getRecords().stream()
                .map(CustomerOrder::getId).collect(Collectors.toList()));
        List<OrderResponses.OrderItem> records = result.getRecords().stream()
                .map(order -> item(order, counts.get(order.getId()))).collect(Collectors.toList());
        return new OrderResponses.Page(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    public OrderResponses.Detail detail(Long id) {
        CustomerOrder order = requireOrder(id);
        List<OrderTask> orderTasks = taskMapper.selectList(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, id).orderByAsc(OrderTask::getTaskSeq));
        List<OrderResponses.TaskItem> tasks = orderTasks.stream().map(this::taskItem).collect(Collectors.toList());
        OrderTask current = orderTasks.stream().filter(task -> task.getStatus() == OrderTaskStatus.RUNNING)
                .findFirst().orElseGet(() -> orderTasks.stream()
                        .filter(task -> task.getStatus() == OrderTaskStatus.QUEUED).findFirst().orElse(null));
        OrderTaskCount count = taskCounts(Collections.singletonList(id)).get(id);
        return new OrderResponses.Detail(item(order, count), tasks,
                current == null ? null : taskItem(current), executionConfig(current),
                order.getErrorCode(), order.getErrorMessage(), order.getUpstreamUpdatedAt());
    }

    public List<OrderResponses.TaskItem> tasks(Long orderId) {
        requireOrder(orderId);
        return taskMapper.selectList(Wrappers.<OrderTask>lambdaQuery()
                        .eq(OrderTask::getOrderId, orderId).orderByAsc(OrderTask::getTaskSeq))
                .stream().map(this::taskItem).collect(Collectors.toList());
    }

    public OrderResponses.TaskSummary summary(Long orderId) {
        requireOrder(orderId);
        List<OrderTask> active = taskMapper.selectList(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, orderId)
                .in(OrderTask::getStatus, OrderTaskStatus.RUNNING, OrderTaskStatus.QUEUED)
                .orderByAsc(OrderTask::getTaskSeq));
        OrderTaskCount count = taskCounts(Collections.singletonList(orderId)).get(orderId);
        int total = count == null || count.getTaskCount() == null ? 0 : count.getTaskCount();
        int completed = count == null || count.getCompletedTaskCount() == null ? 0 : count.getCompletedTaskCount();
        return new OrderResponses.TaskSummary(orderId, total, completed,
                active.isEmpty() ? null : taskItem(active.get(0)));
    }

    private OrderResponses.ExecutionConfig executionConfig(OrderTask task) {
        if (task == null) return null;
        FlowTemplate flow = task.getFlowTemplateId() == null ? null : flowTemplateMapper.selectById(task.getFlowTemplateId());
        if (flow == null && StringUtils.isNotBlank(task.getFlowNumber())) {
            flow = flowTemplateMapper.selectOne(Wrappers.<FlowTemplate>lambdaQuery()
                    .eq(FlowTemplate::getTemplateNumber, task.getFlowNumber()).last("limit 1"));
        }
        WorkflowTemplateEntity template = flow == null || flow.getSourceTemplateId() == null
                ? null : workflowTemplateMapper.selectById(flow.getSourceTemplateId());
        List<FlowNode> nodes = template == null ? Collections.emptyList()
                : flowNodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery()
                        .eq(FlowNode::getTemplateId, template.getId()).orderByAsc(FlowNode::getSort));
        List<OrderResponses.ActionItem> actions = nodes.stream().map(node -> new OrderResponses.ActionItem(
                node.getId(), node.getSort(), node.getNodeName(), node.getNodeCode(),
                node.getStatus() == null ? null : node.getStatus().getLabel(), node.getCompletionCriteria(),
                node.getFailureStrategy() == null ? null : node.getFailureStrategy().getLabel()))
                .collect(Collectors.toList());
        String path = nodes.stream().map(FlowNode::getNodeName).filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" → "));
        String strategies = nodes.stream().map(FlowNode::getFailureStrategy).filter(Objects::nonNull)
                .map(value -> value.getLabel()).distinct().collect(Collectors.joining("；"));
        return new OrderResponses.ExecutionConfig(task.getFlowNumber(), flow == null ? null : flow.getTemplateName(),
                flow == null ? task.getFlowTemplateId() : flow.getId(), template == null ? null : template.getTemplateName(),
                path, task.getCurrentStep(), strategies, actions);
    }

    private OrderResponses.OrderItem item(CustomerOrder value, OrderTaskCount count) {
        int total = count == null || count.getTaskCount() == null ? 0 : count.getTaskCount();
        int completed = count == null || count.getCompletedTaskCount() == null ? 0 : count.getCompletedTaskCount();
        return new OrderResponses.OrderItem(value.getId(), value.getUpstreamOrderNo(), value.getSystemOrderNo(),
                value.getSource(), value.getStatus(), value.getPriority(), total, completed,
                completed + " / " + total, value.getIssuedAt(), value.getUpdateTime());
    }

    private OrderResponses.TaskItem taskItem(OrderTask value) {
        String taskNumber = String.format("TRN-%04d-%02d", value.getOrderId(), value.getTaskSeq());
        return new OrderResponses.TaskItem(value.getId(), taskNumber, value.getTaskSeq(), value.getTaskName(),
                value.getFlowNumber(), value.getStatus(), value.getCurrentStep(), value.getStartedAt(),
                value.getCompletedAt(), value.getUpdateTime(), value.getErrorMessage());
    }

    private Map<Long, OrderTaskCount> taskCounts(List<Long> orderIds) {
        Map<Long, OrderTaskCount> result = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) return result;
        for (OrderTaskCount count : taskMapper.countByOrderIds(orderIds)) result.put(count.getOrderId(), count);
        return result;
    }

    private CustomerOrder requireOrder(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) throw new NoSuchElementException("订单不存在: " + id);
        return order;
    }
}

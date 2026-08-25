package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.workflow.order.api.OrderResponses;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class OrderQueryService {
    private final CustomerOrderMapper orderMapper;
    private final OrderTaskMapper taskMapper;

    public OrderQueryService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
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
        List<OrderResponses.OrderItem> records = result.getRecords().stream().map(this::item).collect(Collectors.toList());
        return new OrderResponses.Page(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    public OrderResponses.Detail detail(Long id) {
        CustomerOrder order = requireOrder(id);
        return new OrderResponses.Detail(item(order), order.getErrorCode(), order.getErrorMessage(), order.getUpstreamUpdatedAt());
    }

    public List<OrderResponses.TaskItem> tasks(Long orderId) {
        requireOrder(orderId);
        return taskMapper.selectList(Wrappers.<OrderTask>lambdaQuery()
                        .eq(OrderTask::getOrderId, orderId).orderByAsc(OrderTask::getTaskSeq))
                .stream().map(this::taskItem).collect(Collectors.toList());
    }

    public OrderResponses.TaskSummary summary(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        List<OrderTask> active = taskMapper.selectList(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getOrderId, orderId)
                .in(OrderTask::getStatus, OrderTaskStatus.RUNNING, OrderTaskStatus.QUEUED)
                .orderByAsc(OrderTask::getTaskSeq));
        return new OrderResponses.TaskSummary(orderId, order.getTaskCount(), order.getCompletedTaskCount(),
                active.isEmpty() ? null : taskItem(active.get(0)));
    }

    private OrderResponses.OrderItem item(CustomerOrder value) {
        int total = value.getTaskCount() == null ? 0 : value.getTaskCount();
        int completed = value.getCompletedTaskCount() == null ? 0 : value.getCompletedTaskCount();
        return new OrderResponses.OrderItem(value.getId(), value.getUpstreamOrderNo(), value.getSystemOrderNo(),
                value.getSource(), value.getStatus(), value.getPriority(), total, completed,
                completed + " / " + total, value.getIssuedAt(), value.getUpdateTime());
    }

    private OrderResponses.TaskItem taskItem(OrderTask value) {
        return new OrderResponses.TaskItem(value.getId(), value.getTaskSeq(), value.getTaskName(),
                value.getFlowNumber(), value.getStatus(),
                value.getCurrentStep(), value.getStartedAt(),
                value.getCompletedAt(), value.getUpdateTime(), value.getErrorMessage());
    }

    private CustomerOrder requireOrder(Long id) {
        CustomerOrder order = orderMapper.selectById(id);
        if (order == null) throw new NoSuchElementException("订单不存在: " + id);
        return order;
    }
}

package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.workflow.order.client.PulledOrder;
import com.kunling.scheduling.workflow.order.client.PulledTask;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderPersistenceService {
    private final CustomerOrderMapper orderMapper;
    private final OrderTaskMapper taskMapper;

    public OrderPersistenceService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public OrderPersistResult persist(String requestedSource, PulledOrder pulled) {
        validate(requestedSource, pulled);
        String source = StringUtils.defaultIfBlank(pulled.getSource(), requestedSource).trim();
        CustomerOrder existing = findOrder(source, pulled.getUpstreamOrderNo());
        if (existing != null) {
            return new OrderPersistResult(existing.getId(), false, false);
        }

        CustomerOrder order = new CustomerOrder();
        order.setSource(source);
        order.setUpstreamOrderNo(pulled.getUpstreamOrderNo().trim());
        order.setSystemOrderNo(number("SYS-ORD"));
        order.setStatus(OrderStatus.QUEUED);
        order.setPriority(pulled.getPriority());
        order.setIssuedAt(pulled.getIssuedAt());
        order.setUpstreamUpdatedAt(pulled.getUpstreamUpdatedAt());
        order.setVersion(0);
        order.setIsDeleted(0);

        if (!insertOrder(order)) {
            CustomerOrder concurrent = findOrder(source, pulled.getUpstreamOrderNo());
            return new OrderPersistResult(concurrent.getId(), false, false);
        }

        for (PulledTask pulledTask : pulled.getTasks()) {
            insertTask(order.getId(), pulledTask);
        }
        return new OrderPersistResult(order.getId(), true, true);
    }

    private void insertTask(Long orderId, PulledTask pulled) {
        OrderTask task = new OrderTask();
        task.setOrderId(orderId);
        task.setTaskSeq(pulled.getTaskSeq());
        task.setTaskName(pulled.getTaskName().trim());
        task.setFlowNumber(pulled.getFlowNumber().trim());
        task.setFlowTemplateId(pulled.getFlowTemplateId());
        task.setStatus(OrderTaskStatus.QUEUED);
        task.setVersion(0);
        task.setIsDeleted(0);
        taskMapper.insert(task);
    }

    private boolean insertOrder(CustomerOrder order) {
        try {
            orderMapper.insert(order);
            return true;
        } catch (DuplicateKeyException duplicate) {
            if (findOrder(order.getSource(), order.getUpstreamOrderNo()) == null) throw duplicate;
            return false;
        }
    }

    private CustomerOrder findOrder(String source, String upstreamOrderNo) {
        return orderMapper.selectOne(Wrappers.<CustomerOrder>lambdaQuery()
                .eq(CustomerOrder::getSource, source)
                .eq(CustomerOrder::getUpstreamOrderNo, upstreamOrderNo));
    }

    private void validate(String requestedSource, PulledOrder order) {
        if (order == null || StringUtils.isBlank(requestedSource)
                || StringUtils.isBlank(order.getUpstreamOrderNo())) {
            throw new IllegalArgumentException("订单来源和上游订单号不能为空");
        }
        if (order.getTasks() == null || order.getTasks().isEmpty()) {
            throw new IllegalArgumentException("订单必须至少包含一个任务: " + order.getUpstreamOrderNo());
        }
        if (order.getPriority() == null || order.getPriority() < 1 || order.getPriority() > 4) {
            throw new IllegalArgumentException("订单优先级只能是1、2、3、4");
        }
        Set<Integer> sequences = new HashSet<>();
        for (PulledTask task : order.getTasks()) {
            if (task == null || task.getTaskSeq() == null || task.getTaskSeq() < 1
                    || StringUtils.isBlank(task.getTaskName())
                    || StringUtils.isBlank(task.getFlowNumber())) {
                throw new IllegalArgumentException("任务顺序、名称和流程编号不能为空");
            }
            if (!sequences.add(task.getTaskSeq())) throw new IllegalArgumentException("订单内taskSeq重复: " + task.getTaskSeq());
        }
    }

    private String number(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return prefix + "-" + timestamp + "-" + suffix;
    }

}

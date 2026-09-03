package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
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
    private final FlowTemplateMapper flowTemplateMapper;
    private final ObjectMapper objectMapper;

    public OrderPersistenceService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper,
                                   FlowTemplateMapper flowTemplateMapper, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
        this.flowTemplateMapper = flowTemplateMapper;
        this.objectMapper = objectMapper;
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
        FlowTemplate template = flowTemplateMapper.selectById(pulled.getFlowTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("流程模板不存在: " + pulled.getFlowTemplateId());
        }
        OrderTask task = new OrderTask();
        task.setOrderId(orderId);
        task.setTaskSeq(pulled.getTaskSeq());
        task.setTaskName(StringUtils.defaultIfBlank(pulled.getTaskName(), template.getTemplateName()).trim());
        task.setFlowNumber(StringUtils.defaultIfBlank(pulled.getFlowNumber(), template.getTemplateNumber()).trim());
        task.setFlowTemplateId(pulled.getFlowTemplateId());
        task.setTaskParams(writeJson(pulled.getItems()));
        task.setStatus(OrderTaskStatus.QUEUED);
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
        if (order.getPriority() == null) {
            order.setPriority(1);
        }
        if (order.getPriority() < 1 || order.getPriority() > 4) {
            throw new IllegalArgumentException("订单优先级只能是1、2、3、4");
        }
        Set<Integer> sequences = new HashSet<>();
        for (int index = 0; index < order.getTasks().size(); index++) {
            PulledTask task = order.getTasks().get(index);
            if (task == null || task.getFlowTemplateId() == null) {
                throw new IllegalArgumentException("params中的flowTemplateId不能为空");
            }
            // 上游params数组顺序就是任务执行顺序；未显式传taskSeq时自动从1编号。
            if (task.getTaskSeq() == null) task.setTaskSeq(index + 1);
            if (task.getTaskSeq() < 1) throw new IllegalArgumentException("taskSeq不能小于1");
            if (!sequences.add(task.getTaskSeq())) throw new IllegalArgumentException("订单内taskSeq重复: " + task.getTaskSeq());
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("任务items参数无法转换为JSON", exception);
        }
    }

    private String number(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return prefix + "-" + timestamp + "-" + suffix;
    }

}

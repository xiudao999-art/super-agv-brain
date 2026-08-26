package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.enums.NodeState;
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
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.GraphicInfo;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;

    public OrderService(CustomerOrderMapper orderMapper, OrderTaskMapper taskMapper,
                        FlowTemplateMapper flowTemplateMapper, WorkflowTemplateMapper workflowTemplateMapper,
                        FlowNodeMapper flowNodeMapper, ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.taskMapper = taskMapper;
        this.flowTemplateMapper = flowTemplateMapper;
        this.workflowTemplateMapper = workflowTemplateMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.objectMapper = objectMapper;
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
        FlowTemplate flowTemplate = task.getFlowTemplateId() == null ? null : flowTemplateMapper.selectById(task.getFlowTemplateId());
        if (flowTemplate == null && StringUtils.isNotBlank(task.getFlowNumber())) {
            flowTemplate = flowTemplateMapper.selectOne(Wrappers.<FlowTemplate>lambdaQuery()
                    .eq(FlowTemplate::getTemplateNumber, task.getFlowNumber()).last("limit 1"));
        }
        if (flowTemplate == null){
            return null;
        }
        WorkflowTemplateEntity template = flowTemplate.getSourceTemplateId() == null
                ? null : workflowTemplateMapper.selectById(flowTemplate.getSourceTemplateId());
        List<FlowNode> runtimeNodes = flowNodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTaskId, task.getId()).orderByAsc(FlowNode::getSort));
        List<OrderResponses.ActionItem> actions = xmlActions(template, runtimeNodes);
        String path = xmlCompletePath(template);
        String strategies = actions.stream().map(OrderResponses.ActionItem::getFailureStrategy)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.joining("；"));
        String completionCriteria = actions.stream().map(OrderResponses.ActionItem::getCompletionCriteria)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.joining("；"));
        return new OrderResponses.ExecutionConfig(task.getFlowNumber(), flowTemplate == null ? null : flowTemplate.getTemplateName(),
                template == null ? null : template.getTemplateName(),
                path, resolveCurrentStep(task, runtimeNodes), strategies, completionCriteria, actions,
                template == null ? null : template.getBpmnXml(), parseBpmnProcesses(template));
    }

    private List<OrderResponses.ActionItem> xmlActions(WorkflowTemplateEntity template, List<FlowNode> runtimeNodes) {
        if (template == null || StringUtils.isBlank(template.getBpmnXml())) return Collections.emptyList();
        Map<String, FlowNode> runtimeByCode = runtimeNodes.stream()
                .filter(node -> StringUtils.isNotBlank(node.getNodeCode()))
                .collect(Collectors.toMap(FlowNode::getNodeCode, node -> node, (first, second) -> second));
        Map<String, JsonNode> templateProperties = templateNodeProperties(template);
        BpmnModel model = parseBpmnModel(template);
        List<FlowElement> xmlNodes = new ArrayList<>();
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            collectActionElements(process.getFlowElements(), xmlNodes);
        }
        List<OrderResponses.ActionItem> result = new ArrayList<>();
        for (int i = 0; i < xmlNodes.size(); i++) {
            FlowElement xmlNode = xmlNodes.get(i);
            FlowNode runtime = runtimeByCode.get(xmlNode.getId());
            JsonNode configured = templateProperties.get(xmlNode.getId());
            int sort = i + 1;
            String name = StringUtils.defaultIfBlank(xmlNode.getName(), xmlNode.getId());
            String resource = runtime == null ? xmlNode.getId()
                    : StringUtils.defaultIfBlank(runtime.getNodeCode(), actionResource(runtime));
            String completionCriteria = StringUtils.firstNonBlank(
                    xmlNodeProperty(xmlNode, "completionCriteria"),
                    jsonText(configured, "completionCriteria"));
            String failureStrategy = failureStrategyLabel(StringUtils.firstNonBlank(
                    xmlNodeProperty(xmlNode, "failureStrategy"),
                    jsonText(configured, "failureStrategy")));
            result.add(new OrderResponses.ActionItem(runtime == null ? null : runtime.getId(),
                    String.format("A%02d", sort), sort, name, resource, name, xmlNode.getId(),
                    runtime == null || runtime.getStatus() == null
                            ? NodeState.PENDING.getLabel() : runtime.getStatus().getLabel(),
                    completionCriteria, completionCriteria, failureStrategy));
        }
        return result;
    }

    /** 读取BPMN节点上的自定义属性或extensionElements配置。 */
    private String xmlNodeProperty(FlowElement node, String propertyName) {
        String direct = extensionAttributeValue(node.getAttributes(), propertyName);
        if (StringUtils.isNotBlank(direct)) return direct;
        for (List<ExtensionElement> elements : node.getExtensionElements().values()) {
            for (ExtensionElement element : elements) {
                String value = extensionElementValue(element, propertyName);
                if (StringUtils.isNotBlank(value)) return value;
            }
        }
        return null;
    }

    private String extensionElementValue(ExtensionElement element, String propertyName) {
        if (propertyName.equals(element.getName())) {
            String text = StringUtils.trimToNull(element.getElementText());
            if (text != null) return text;
            String value = extensionAttributeValue(element.getAttributes(), "value");
            if (value != null) return value;
        }
        String configuredName = extensionAttributeValue(element.getAttributes(), "name");
        if (propertyName.equals(configuredName)) {
            String value = extensionAttributeValue(element.getAttributes(), "value");
            return StringUtils.defaultIfBlank(value, StringUtils.trimToNull(element.getElementText()));
        }
        for (List<ExtensionElement> children : element.getChildElements().values()) {
            for (ExtensionElement child : children) {
                String value = extensionElementValue(child, propertyName);
                if (StringUtils.isNotBlank(value)) return value;
            }
        }
        return null;
    }

    private String extensionAttributeValue(Map<String, List<ExtensionAttribute>> attributes, String name) {
        for (Map.Entry<String, List<ExtensionAttribute>> entry : attributes.entrySet()) {
            for (ExtensionAttribute attribute : entry.getValue()) {
                if (name.equals(entry.getKey()) || name.equals(attribute.getName())) {
                    return StringUtils.trimToNull(attribute.getValue());
                }
            }
        }
        return null;
    }

    private Map<String, JsonNode> templateNodeProperties(WorkflowTemplateEntity template) {
        if (template == null || StringUtils.isBlank(template.getEditorData())) return Collections.emptyMap();
        try {
            JsonNode properties = objectMapper.readTree(template.getEditorData()).path("nodeProperties");
            if (!properties.isObject()) return Collections.emptyMap();
            Map<String, JsonNode> result = new HashMap<>();
            properties.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("流程模板editorData解析失败: " + template.getTemplateNumber(), exception);
        }
    }

    private String jsonText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
        return StringUtils.trimToNull(node.path(field).asText());
    }

    private String failureStrategyLabel(String strategy) {
        if (StringUtils.isBlank(strategy)) return null;
        if ("RETRY_THEN_SUSPEND".equals(strategy)) return "按策略重试后挂起";
        if ("SUSPEND_AFTER_RETRYING".equals(strategy)) return "重试后挂起";
        if ("NOTIFY_OPERATORS".equals(strategy)) return "立即挂起并通知操作人员";
        return strategy;
    }

    /** 完整路径用于页面展示，包含开始和结束；动作列表仍排除开始和结束。 */
    private String xmlCompletePath(WorkflowTemplateEntity template) {
        if (template == null || StringUtils.isBlank(template.getBpmnXml())) return null;
        BpmnModel model = parseBpmnModel(template);
        List<String> startNames = new ArrayList<>();
        List<String> actionNames = new ArrayList<>();
        List<String> endNames = new ArrayList<>();
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            collectPathNames(process.getFlowElements(), startNames, actionNames, endNames);
        }
        List<String> names = new ArrayList<>(startNames.size() + actionNames.size() + endNames.size());
        names.addAll(startNames);
        names.addAll(actionNames);
        names.addAll(endNames);
        return names.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" → "));
    }

    private void collectPathNames(Iterable<FlowElement> elements, List<String> startNames,
                                  List<String> actionNames, List<String> endNames) {
        for (FlowElement element : elements) {
            if (element instanceof SequenceFlow) continue;
            String name = StringUtils.defaultIfBlank(element.getName(), element.getId());
            if (element instanceof StartEvent) {
                startNames.add(name);
            } else if (element instanceof EndEvent) {
                endNames.add(name);
            } else {
                actionNames.add(name);
            }
            if (element instanceof SubProcess) {
                collectPathNames(((SubProcess) element).getFlowElements(), startNames, actionNames, endNames);
            }
        }
    }

    private void collectActionElements(Iterable<FlowElement> elements, List<FlowElement> result) {
        for (FlowElement element : elements) {
            if (element instanceof SequenceFlow || element instanceof StartEvent || element instanceof EndEvent) continue;
            result.add(element);
            if (element instanceof SubProcess) {
                collectActionElements(((SubProcess) element).getFlowElements(), result);
            }
        }
    }

    private BpmnModel parseBpmnModel(WorkflowTemplateEntity template) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(template.getBpmnXml().getBytes(StandardCharsets.UTF_8));
            return new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(input), true, true);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("流程模板BPMN XML解析失败: " + template.getTemplateNumber(), exception);
        }
    }

    private List<OrderResponses.BpmnProcess> parseBpmnProcesses(WorkflowTemplateEntity template) {
        if (template == null || StringUtils.isBlank(template.getBpmnXml())) return Collections.emptyList();
        try {
            BpmnModel model = parseBpmnModel(template);
            List<OrderResponses.BpmnProcess> result = new ArrayList<>();
            for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
                List<OrderResponses.BpmnNode> bpmnNodes = new ArrayList<>();
                List<OrderResponses.BpmnFlow> bpmnFlows = new ArrayList<>();
                collectFlowElements(model, process.getFlowElements(), null, bpmnNodes, bpmnFlows);
                result.add(new OrderResponses.BpmnProcess(process.getId(), process.getName(), bpmnNodes, bpmnFlows));
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("流程模板BPMN XML解析失败: " + template.getTemplateNumber(), exception);
        }
    }

    private void collectFlowElements(BpmnModel model, Iterable<FlowElement> elements, String parentSubProcessId,
                                     List<OrderResponses.BpmnNode> nodes, List<OrderResponses.BpmnFlow> flows) {
        for (FlowElement element : elements) {
            if (element instanceof SequenceFlow) {
                SequenceFlow flow = (SequenceFlow) element;
                flows.add(new OrderResponses.BpmnFlow(flow.getId(), flow.getName(), flow.getSourceRef(),
                        flow.getTargetRef(), flow.getConditionExpression(), parentSubProcessId));
            } else {
                GraphicInfo graphic = model.getGraphicInfo(element.getId());
                nodes.add(new OrderResponses.BpmnNode(element.getId(), element.getName(),
                        element.getClass().getSimpleName(), parentSubProcessId,
                        graphic == null ? null : graphic.getX(), graphic == null ? null : graphic.getY(),
                        graphic == null ? null : graphic.getWidth(), graphic == null ? null : graphic.getHeight()));
                if (element instanceof SubProcess) {
                    SubProcess subProcess = (SubProcess) element;
                    collectFlowElements(model, subProcess.getFlowElements(), subProcess.getId(), nodes, flows);
                }
            }
        }
    }

    private String actionResource(FlowNode node) {
        if (node.getActions() == null || node.getActions().isEmpty()) return null;
        return node.getActions().stream().map(id -> "RESOURCE-" + id).collect(Collectors.joining(", "));
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
        String currentStep = resolveCurrentStep(value, null);
        return new OrderResponses.TaskItem(value.getId(), taskNumber, value.getTaskSeq(), value.getTaskName(),
                value.getFlowNumber(), value.getStatus(), currentStep, value.getStartedAt(),
                value.getCompletedAt(), value.getUpdateTime(), value.getErrorMessage());
    }

    /**
     * 当前步骤不再存order_task，而是取该任务最后一条flow_node。
     */
    private String resolveCurrentStep(OrderTask task, List<FlowNode> loadedNodes) {
        if (task.getStatus() == OrderTaskStatus.SUCCEEDED) return "结束";
        List<FlowNode> nodes = loadedNodes;
        if (nodes == null) {
            FlowNode latest = flowNodeMapper.selectOne(Wrappers.<FlowNode>lambdaQuery()
                    .eq(FlowNode::getTaskId, task.getId())
                    .orderByDesc(FlowNode::getSort).orderByDesc(FlowNode::getId).last("limit 1"));
            nodes = latest == null ? Collections.emptyList() : Collections.singletonList(latest);
        }
        if (nodes.isEmpty()) {
            return task.getStatus() == OrderTaskStatus.QUEUED ? "等待前序任务完成" : null;
        }
        FlowNode latest = nodes.get(nodes.size() - 1);
        return StringUtils.defaultIfBlank(latest.getNodeName(), latest.getNodeCode());
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

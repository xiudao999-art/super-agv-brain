package com.kunling.scheduling.workflow.action;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** 验证流程模板中的可执行节点已绑定 Action 定义。 */
@Component
public class WorkflowActionBindingValidator {

    static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";
    private static final int MAX_ACTION_DEFINITION_ID_LENGTH = 36;

    /**
     * receiveTask 是当前流程引擎与 Action 的唯一自动执行节点。
     * 人工任务、网关、事件和子流程容器不需要绑定 Action。
     */
    public void validate(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) {
            throw new IllegalArgumentException("BPMN XML不能为空");
        }
        Document document = parse(bpmnXml);
        NodeList receiveTasks = document.getElementsByTagNameNS("*", "receiveTask");
        for (int index = 0; index < receiveTasks.getLength(); index++) {
            Element receiveTask = (Element) receiveTasks.item(index);
            String nodeId = StringUtils.defaultIfBlank(receiveTask.getAttribute("id"), "<unknown>");
            String actionDefinitionId = StringUtils.trimToNull(
                    receiveTask.getAttributeNS(FLOWABLE_NAMESPACE, "actionDefinitionId"));
            if (actionDefinitionId == null) {
                throw new IllegalArgumentException(
                        "可执行节点未绑定 actionDefinitionId: " + nodeId);
            }
            if (actionDefinitionId.length() > MAX_ACTION_DEFINITION_ID_LENGTH) {
                throw new IllegalArgumentException(
                        "节点 actionDefinitionId 长度不能超过 36: " + nodeId);
            }
        }
    }

    private Document parse(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN XML格式错误", exception);
        }
    }
}

package com.kunling.scheduling.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 后端再次校验编辑器生成的受控异常分支，拒绝任意Flowable表达式。 */
final class WorkflowTemplateValidator {
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final Set<String> TYPES = set("SUCCESS", "RULE", "DEFAULT_EXCEPTION");
    private static final Set<String> FIELDS = set("eventCode", "businessCode", "reasonCode");
    private static final Set<String> EVENTS = set("RETRYABLE", "MANUAL_INTERVENTION", "NON_RETRYABLE", "CRITICAL");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    private WorkflowTemplateValidator() { }

    static void validate(String xml, JsonNode editorData) {
        Document document = parse(xml);
        Element process = first(document, "process");
        if (process == null) throw new IllegalArgumentException("BPMN XML缺少process元素");
        Map<String, Element> main = directById(process);
        List<Element> gateways = all(document, "exclusiveGateway");
        List<Element> exceptionGateways = new ArrayList<Element>();
        for (Element gateway : gateways) if (isExceptionGateway(gateway)) {
            if (gateway.getParentNode() != process) throw new IllegalArgumentException("异常判断节点只能位于主流程");
            exceptionGateways.add(gateway);
        }
        if (exceptionGateways.isEmpty()) return;
        if (editorData == null || !editorData.path("nodes").isArray() || !editorData.path("connections").isArray()) {
            throw new IllegalArgumentException("异常判断模板必须包含结构化editorData");
        }
        Map<String, JsonNode> editorNodes = new HashMap<String, JsonNode>();
        for (JsonNode node : editorData.path("nodes")) editorNodes.put(node.path("bpmnNodeId").asText(), node);
        List<JsonNode> connections = new ArrayList<JsonNode>();
        for (JsonNode connection : editorData.path("connections")) connections.add(connection);
        for (Element gateway : exceptionGateways) validateGateway(gateway, main, editorNodes, connections);
        for (JsonNode node : editorData.path("nodes")) if ("EXCEPTION_GATEWAY".equals(node.path("kind").asText())) {
            Element element = main.get(node.path("bpmnNodeId").asText());
            if (element == null || !"exclusiveGateway".equals(local(element)) || !isExceptionGateway(element)) {
                throw new IllegalArgumentException("editorData中的异常判断节点在BPMN中不存在");
            }
        }
    }

    private static void validateGateway(Element gateway, Map<String, Element> main,
                                        Map<String, JsonNode> editorNodes, List<JsonNode> connections) {
        String id = gateway.getAttribute("id");
        JsonNode editorNode = editorNodes.get(id);
        if (editorNode == null || !"EXCEPTION_GATEWAY".equals(editorNode.path("kind").asText())) {
            throw new IllegalArgumentException("异常判断节点的editorData与BPMN不一致: " + id);
        }
        List<Element> incoming = flows(main, "targetRef", id), outgoing = flows(main, "sourceRef", id);
        if (incoming.size() != 1) throw new IllegalArgumentException("异常判断节点必须且只能有一个前置动作: " + id);
        Element predecessor = main.get(incoming.get(0).getAttribute("sourceRef"));
        if (predecessor == null || !"receiveTask".equals(local(predecessor))) {
            throw new IllegalArgumentException("异常判断节点必须直接连接在顶层普通动作之后: " + id);
        }
        List<JsonNode> branches = new ArrayList<JsonNode>();
        for (JsonNode connection : connections) if (id.equals(connection.path("sourceRef").asText())) branches.add(connection);
        int success = 0, rules = 0, defaults = 0;
        Set<Integer> priorities = new HashSet<Integer>();
        for (JsonNode branch : branches) {
            String type = branch.path("branchType").asText();
            if (!TYPES.contains(type)) throw new IllegalArgumentException("异常分支类型不支持: " + type);
            if ("SUCCESS".equals(type)) success++;
            if ("DEFAULT_EXCEPTION".equals(type)) defaults++;
            if ("RULE".equals(type)) {
                rules++;
                int priority = branch.path("priority").asInt(0);
                if (priority < 1 || !priorities.add(priority)) throw new IllegalArgumentException("异常规则优先级必须唯一且大于0: " + id);
                validateRules(branch);
            }
            if (!"SUCCESS".equals(type)) {
                Element target = main.get(branch.path("targetRef").asText());
                if (target != null && "endEvent".equals(local(target))) throw new IllegalArgumentException("异常分支不能直接连接结束节点");
            }
        }
        if (success != 1 || rules < 1 || defaults != 1 || branches.size() != outgoing.size()) {
            throw new IllegalArgumentException("异常判断节点必须包含正常、规则和其他异常三类分支: " + id);
        }
        List<JsonNode> ordered = new ArrayList<JsonNode>();
        for (JsonNode branch : branches) if ("RULE".equals(branch.path("branchType").asText())) ordered.add(branch);
        Collections.sort(ordered, Comparator.comparingInt(value -> value.path("priority").asInt()));
        for (JsonNode branch : branches) validateXmlBranch(gateway, outgoing, branch, ordered);
    }

    private static void validateRules(JsonNode branch) {
        JsonNode rules = branch.path("rules");
        if (!rules.isArray() || rules.size() == 0) throw new IllegalArgumentException("异常规则至少需要一个条件");
        Set<String> used = new HashSet<String>();
        for (JsonNode rule : rules) {
            String field = rule.path("field").asText(), operator = rule.path("operator").asText(), value = rule.path("value").asText();
            if (!FIELDS.contains(field) || !used.add(field) || !"EQ".equals(operator) || !CODE.matcher(value).matches()) {
                throw new IllegalArgumentException("异常规则字段、比较方式或值不合法");
            }
            if ("eventCode".equals(field) && !EVENTS.contains(value)) throw new IllegalArgumentException("异常分类不支持: " + value);
        }
    }

    private static void validateXmlBranch(Element gateway, List<Element> outgoing, JsonNode branch, List<JsonNode> ordered) {
        String type = branch.path("branchType").asText(), target = branch.path("targetRef").asText();
        Element flow = null;
        for (Element candidate : outgoing) if (target.equals(candidate.getAttribute("targetRef")) && type.equals(attr(candidate, "branchType"))) flow = candidate;
        if (flow == null) throw new IllegalArgumentException("异常分支在BPMN中不存在: " + branch.path("label").asText());
        String condition = condition(flow);
        if ("DEFAULT_EXCEPTION".equals(type)) {
            if (!flow.getAttribute("id").equals(gateway.getAttribute("default")) || condition != null) throw new IllegalArgumentException("其他异常分支必须是无表达式的默认线");
            return;
        }
        String expected = "SUCCESS".equals(type) ? "actionResult['success'] == true" : effective(branch, ordered);
        if (!normalize(expected).equals(normalize(condition))) throw new IllegalArgumentException("异常分支表达式不是系统生成的受控表达式");
    }

    private static String effective(JsonNode branch, List<JsonNode> ordered) {
        String raw = raw(branch);
        List<String> previous = new ArrayList<String>();
        int priority = branch.path("priority").asInt();
        for (JsonNode value : ordered) if (value.path("priority").asInt() < priority) previous.add("(" + raw(value) + ")");
        return previous.isEmpty() ? raw : "(" + raw + ") and not (" + StringUtils.join(previous, " or ") + ")";
    }

    private static String raw(JsonNode branch) {
        List<String> parts = new ArrayList<String>();
        parts.add("(actionResult['success'] == false)");
        for (JsonNode rule : branch.path("rules")) parts.add("(actionResult['" + rule.path("field").asText() + "'] == '" + rule.path("value").asText() + "')");
        return StringUtils.join(parts, " and ");
    }

    private static String condition(Element flow) {
        for (Element child : direct(flow)) if ("conditionExpression".equals(local(child))) return child.getTextContent();
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.startsWith("${") && result.endsWith("}")) result = result.substring(2, result.length() - 1);
        return result.replaceAll("\\s+", "");
    }

    private static List<Element> flows(Map<String, Element> main, String attribute, String value) {
        List<Element> result = new ArrayList<Element>();
        for (Element element : main.values()) if ("sequenceFlow".equals(local(element)) && value.equals(element.getAttribute(attribute))) result.add(element);
        return result;
    }

    private static Map<String, Element> directById(Element parent) {
        Map<String, Element> result = new LinkedHashMap<String, Element>();
        for (Element element : direct(parent)) if (StringUtils.isNotBlank(element.getAttribute("id"))) result.put(element.getAttribute("id"), element);
        return result;
    }

    private static List<Element> direct(Element parent) {
        List<Element> result = new ArrayList<Element>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) if (children.item(i).getNodeType() == Node.ELEMENT_NODE) result.add((Element) children.item(i));
        return result;
    }

    private static List<Element> all(Document document, String name) {
        List<Element> result = new ArrayList<Element>();
        NodeList nodes = document.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }

    private static Element first(Document document, String name) { List<Element> values = all(document, name); return values.isEmpty() ? null : values.get(0); }
    private static String local(Element element) { return element.getLocalName() == null ? element.getNodeName() : element.getLocalName(); }
    private static String attr(Element element, String name) { String value = element.getAttributeNS(FLOWABLE_NS, name); return StringUtils.isNotBlank(value) ? value : element.getAttribute("flowable:" + name); }
    private static boolean isExceptionGateway(Element gateway) { return "true".equalsIgnoreCase(attr(gateway, "exceptionGateway")); }
    private static Set<String> set(String... values) { return new HashSet<String>(Arrays.asList(values)); }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN XML格式错误", exception);
        }
    }
}

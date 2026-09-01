package com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** cnet8 RobotMessage/MessageInfo 方言的唯一协议适配入口。 */
@Component
public class Cnet8ProtocolAdapter {
    public static final int MIN_TIMEOUT_MS = 1;
    public static final int MAX_TIMEOUT_MS = 3_600_000;

    private final ObjectMapper objectMapper;
    private final Cnet8ExecutionPlanRenderer planRenderer;
    private final Cnet8ActionEventNormalizer actionEventNormalizer;

    public Cnet8ProtocolAdapter(ObjectMapper objectMapper,
                                Cnet8ExecutionPlanRenderer planRenderer,
                                Cnet8ActionEventNormalizer actionEventNormalizer) {
        this.objectMapper = objectMapper;
        this.planRenderer = planRenderer;
        this.actionEventNormalizer = actionEventNormalizer;
    }

    public InitialRegistration parseInitialRegistration(JsonNode message) {
        requireMessageType(message, "REGISTER");
        String messageId = requiredText(message, "MessageId", 64);
        String robotId = requiredText(message, "RobotId", 128);
        JsonNode info = requiredObject(message, "MessageInfo");
        requireSameWhenPresent(robotId, info, "RobotId", "REGISTER.MessageInfo.RobotId");
        return new InitialRegistration(messageId, robotId, "cnet8:" + messageId, "COMPOSITE");
    }

    public RegistrationCapabilities parseRobotRegistration(JsonNode message, String expectedRobotId) {
        requireMessageType(message, "RegisterRobot");
        requireRobotId(message, expectedRobotId);
        JsonNode info = requiredObject(message, "MessageInfo");
        requireSameWhenPresent(expectedRobotId, info, "RobotId", "RegisterRobot.MessageInfo.RobotId");
        if (!"2.0".equals(requiredText(info, "ProtocolVersion"))) {
            throw new IllegalArgumentException("cnet8 RegisterRobot.ProtocolVersion 必须为 2.0");
        }
        JsonNode source = info.get("Capabilities");
        if (source == null || !source.isArray() || source.size() == 0) {
            throw new IllegalArgumentException("cnet8 RegisterRobot.Capabilities 必须是非空数组");
        }

        Map<String, RobotOperationCapability> capabilities =
                new LinkedHashMap<String, RobotOperationCapability>();
        for (JsonNode item : source) {
            String operation = requiredText(item, "Operation");
            if (!operation.matches("[A-Z0-9][A-Z0-9._-]{1,127}")) {
                throw new IllegalArgumentException("cnet8 operation 必须是稳定的大写标识：" + operation);
            }
            RobotOperationCapability previous = capabilities.put(operation,
                    new RobotOperationCapability(operation, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS));
            if (previous != null) {
                throw new IllegalArgumentException("cnet8 Capabilities 重复：" + operation);
            }
        }

        Set<String> policyFeatures = new LinkedHashSet<String>();
        policyFeatures.add("RETRY_STEP");
        policyFeatures.add("VERIFY_THEN_RETRY");
        policyFeatures.add("SKIP_STEP");
        policyFeatures.add("STOP_AND_REPORT");
        return new RegistrationCapabilities(capabilities, policyFeatures);
    }

    public ObjectNode createRegisterAck(String robotId,
                                        String sessionId,
                                        int heartbeatIntervalMs,
                                        String messageId) {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("SessionId", sessionId);
        info.put("HeartbeatIntervalMs", heartbeatIntervalMs);
        return envelope("REGISTER_ACK", messageId, robotId, null, null, info);
    }

    public ObjectNode createPong(JsonNode ping,
                                 String robotId,
                                 String sessionId,
                                 String messageId) {
        requireMessageType(ping, "PING");
        requireRobotId(ping, robotId);
        JsonNode pingInfo = requiredObject(ping, "MessageInfo");
        if (!sessionId.equals(requiredText(pingInfo, "SessionId"))) {
            throw new IllegalArgumentException("cnet8 PING.SessionId 与当前连接不一致");
        }
        ObjectNode pongInfo = objectMapper.createObjectNode();
        pongInfo.put("SessionId", sessionId);
        pongInfo.put("Sequence", requiredPositiveLong(pingInfo, "Sequence"));
        pongInfo.put("ServerTime", Instant.now().toString());
        return envelope("PONG", messageId, robotId, null, null, pongInfo);
    }

    public ObjectNode createCommand(RobotActionCommand command, String messageId) {
        ObjectNode plan = planRenderer.render(command);
        return envelope("COMMAND", messageId, command.robotId(),
                command.actionInstanceId(), command.deviceCommandId(), plan);
    }

    public RobotActionEvent parseActionEvent(JsonNode message,
                                             String expectedRobotId,
                                             String sessionId,
                                             long sequence) {
        return actionEventNormalizer.normalize(message, expectedRobotId, sessionId, sequence);
    }

    public String messageType(JsonNode message) {
        return requiredText(message, "MessageType", 32);
    }

    public void validateRobotMessage(JsonNode message, String expectedRobotId) {
        requireRobotId(message, expectedRobotId);
    }

    private ObjectNode envelope(String messageType,
                                String messageId,
                                String robotId,
                                String actionInstanceId,
                                String deviceCommandId,
                                JsonNode messageInfo) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("MessageId", messageId);
        message.put("MessageName", messageType);
        message.put("MessageType", messageType);
        message.put("RobotId", robotId);
        message.put("ActionInstanceId", actionInstanceId == null ? "" : actionInstanceId);
        message.put("DeviceCommandId", deviceCommandId == null ? "" : deviceCommandId);
        message.put("Timestamp", Instant.now().toString());
        message.set("MessageInfo", messageInfo.deepCopy());
        return message;
    }

    private void requireMessageType(JsonNode message, String expected) {
        String actual = requiredText(message, "MessageType", 32);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("cnet8 消息类型必须为 " + expected + "，实际为 " + actual);
        }
    }

    private void requireRobotId(JsonNode message, String expectedRobotId) {
        String actual = requiredText(message, "RobotId", 128);
        if (!expectedRobotId.equals(actual)) {
            throw new IllegalArgumentException("cnet8 RobotId 与当前连接不一致");
        }
    }

    private void requireSameWhenPresent(String expected,
                                        JsonNode parent,
                                        String field,
                                        String label) {
        JsonNode value = parent.get(field);
        if (value != null && value.isTextual() && !value.textValue().trim().isEmpty()
                && !expected.equals(value.textValue())) {
            throw new IllegalArgumentException(label + " 与信封 RobotId 不一致");
        }
    }

    private JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " 必须是 JSON 对象");
        }
        return value;
    }

    private String requiredText(JsonNode parent, String field) {
        return requiredText(parent, field, Integer.MAX_VALUE);
    }

    private String requiredText(JsonNode parent, String field, int maximumLength) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
        if (value.textValue().length() > maximumLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maximumLength);
        }
        return value.textValue();
    }

    private long requiredPositiveLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0L) {
            throw new IllegalArgumentException(field + " 必须是正整数");
        }
        return value.longValue();
    }

    public static final class InitialRegistration {
        private final String messageId;
        private final String robotId;
        private final String clientInstanceId;
        private final String robotType;

        private InitialRegistration(String messageId,
                                    String robotId,
                                    String clientInstanceId,
                                    String robotType) {
            this.messageId = messageId;
            this.robotId = robotId;
            this.clientInstanceId = clientInstanceId;
            this.robotType = robotType;
        }

        public String messageId() { return messageId; }
        public String robotId() { return robotId; }
        public String clientInstanceId() { return clientInstanceId; }
        public String robotType() { return robotType; }
    }

    public static final class RegistrationCapabilities {
        private final Map<String, RobotOperationCapability> operationCapabilities;
        private final Set<String> policyFeatures;

        private RegistrationCapabilities(Map<String, RobotOperationCapability> operationCapabilities,
                                         Set<String> policyFeatures) {
            this.operationCapabilities = Collections.unmodifiableMap(
                    new LinkedHashMap<String, RobotOperationCapability>(operationCapabilities));
            this.policyFeatures = Collections.unmodifiableSet(new LinkedHashSet<String>(policyFeatures));
        }

        public Map<String, RobotOperationCapability> operationCapabilities() {
            return operationCapabilities;
        }

        public Set<String> policyFeatures() { return policyFeatures; }
    }
}

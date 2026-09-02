package com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 将 cnet8 ACTION_EVENT 转为 Action 内部唯一的规范事件。 */
@Component
public class Cnet8ActionEventNormalizer {
    private static final String MOVE_TO_POSE = "MOVE_TO_POSE";
    private static final String[] CARTESIAN_FIELDS = {"x", "y", "z", "rx", "ry", "rz"};
    private static final String[] JOINT_FIELDS = {"j1", "j2", "j3", "j4", "j5", "j6"};

    private final ObjectMapper objectMapper;
    private final Cnet8ClientCodeMapper clientCodeMapper;

    public Cnet8ActionEventNormalizer(ObjectMapper objectMapper,
                                      Cnet8ClientCodeMapper clientCodeMapper) {
        this.objectMapper = objectMapper;
        this.clientCodeMapper = clientCodeMapper;
    }

    public RobotActionEvent normalize(JsonNode message,
                                      String expectedRobotId,
                                      String sessionId,
                                      long sequence) {
        requireTextEquals(message, "MessageType", "ACTION_EVENT");
        requireTextEquals(message, "RobotId", expectedRobotId);
        JsonNode info = requiredObject(message, "MessageInfo");
        String actionInstanceId = consistentIdentity(message, info, "ActionInstanceId", 128);
        String deviceCommandId = consistentIdentity(message, info, "DeviceCommandId", 128);
        PhysicalOutcome outcome = parsePhysicalOutcome(requiredText(info, "PhysicalOutcome"));
        RobotActionEvent.State state = normalizeState(requiredText(info, "State"), outcome);
        String eventKind = requiredText(info, "EventKind");
        JsonNode stepEvent = normalizeStepEvent(eventKind, info.get("LastStepEvent"),
                info.path("Message").asText(null));
        JsonNode resolvedSteps = normalizeResolvedSteps(info.get("ResolvedSteps"));
        JsonNode error = normalizeError(state, info);
        String occurredAt = info.path("OccurredAt").asText(null);
        if (occurredAt == null || occurredAt.trim().isEmpty()) {
            occurredAt = requiredText(message, "Timestamp");
        }
        return new RobotActionEvent(
                "ACTION_EVENT",
                requiredText(message, "MessageId", 64),
                sessionId,
                expectedRobotId,
                actionInstanceId,
                deviceCommandId,
                sequence,
                state,
                stepEvent,
                resolvedSteps,
                outcome,
                error,
                parseTimestamp(occurredAt)
        );
    }

    private JsonNode normalizeStepEvent(String eventKind, JsonNode source, String eventMessage) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("eventType", eventKind);
        putText(target, "message", eventMessage);
        if (source == null || source.isNull()) return target;
        if (!source.isObject()) throw new IllegalArgumentException("cnet8 LastStepEvent 必须是对象");
        putRequiredText(target, "stepId", source, "StepId");
        String operation = requiredText(source, "Operation");
        target.put("operation", operation);
        int attempts = source.path("Attempts").asInt(0);
        if (attempts > 0) target.put("attempt", attempts);
        boolean success = source.path("Success").asBoolean(false);
        boolean skipped = source.path("Skipped").asBoolean(false);
        target.put("stepState", skipped ? "SKIPPED" : success ? "SUCCEEDED" : "FAILED");
        putText(target, "physicalOutcome", source.path("PhysicalOutcome").asText(null));
        putText(target, "message", source.path("Message").asText(eventMessage));
        putText(target, "completedAt", source.path("CompletedAt").asText(null));
        if (source.has("ResultData") && !source.path("ResultData").isNull()) {
            target.set("resultData", normalizeResultData(operation, source.path("ResultData")));
        }
        return target;
    }

    private JsonNode normalizeResolvedSteps(JsonNode source) {
        if (source == null || source.isNull()) return null;
        if (!source.isArray()) throw new IllegalArgumentException("cnet8 ResolvedSteps 必须是数组");
        ArrayNode target = objectMapper.createArrayNode();
        for (JsonNode step : source) target.add(normalizeStepResult(step));
        return target;
    }

    private ObjectNode normalizeStepResult(JsonNode source) {
        if (!source.isObject()) throw new IllegalArgumentException("cnet8 ResolvedSteps 元素必须是对象");
        ObjectNode target = objectMapper.createObjectNode();
        putRequiredText(target, "stepId", source, "StepId");
        String operation = requiredText(source, "Operation");
        target.put("operation", operation);
        target.put("success", source.path("Success").asBoolean(false));
        target.put("skipped", source.path("Skipped").asBoolean(false));
        target.put("attempts", source.path("Attempts").asInt(0));
        putText(target, "physicalOutcome", source.path("PhysicalOutcome").asText(null));
        putText(target, "message", source.path("Message").asText(null));
        putText(target, "completedAt", source.path("CompletedAt").asText(null));
        JsonNode fault = normalizeDeviceFault(source.get("DeviceFault"), null);
        if (fault != null) target.set("deviceFault", fault);
        if (source.has("ResultData") && !source.path("ResultData").isNull()) {
            target.set("resultData", normalizeResultData(operation, source.path("ResultData")));
        }
        return target;
    }

    /**
     * CNET8 信封使用 PascalCase，而 Action 内部规范使用 camelCase。
     * 这里只规范已知的机械臂查询结果字段，未知扩展字段保持原样，避免破坏厂家附加数据。
     */
    private JsonNode normalizeResultData(String operation, JsonNode source) {
        if (!MOVE_TO_POSE.equals(operation) || !source.isObject()) return source.deepCopy();
        ObjectNode target = (ObjectNode) source.deepCopy();
        normalizeField(target, source, "armMoveRequestType");
        normalizeField(target, source, "speedPercent");
        normalizePose(target, source, "armPoseXYZRxRyRz", CARTESIAN_FIELDS);
        normalizePose(target, source, "armPoseJ1J2J3J4J5J6", JOINT_FIELDS);
        return target;
    }

    private void normalizePose(ObjectNode target,
                               JsonNode source,
                               String canonicalField,
                               String[] poseFields) {
        JsonNode poseSource = compatibleField(source, canonicalField);
        if (poseSource == null) return;
        if (!poseSource.isObject()) {
            target.set(canonicalField, poseSource.deepCopy());
        } else {
            ObjectNode normalizedPose = (ObjectNode) poseSource.deepCopy();
            for (String field : poseFields) normalizeField(normalizedPose, poseSource, field);
            target.set(canonicalField, normalizedPose);
        }
        target.remove(pascalCase(canonicalField));
    }

    private void normalizeField(ObjectNode target, JsonNode source, String canonicalField) {
        JsonNode value = compatibleField(source, canonicalField);
        if (value != null) target.set(canonicalField, value.deepCopy());
        target.remove(pascalCase(canonicalField));
    }

    private JsonNode compatibleField(JsonNode source, String canonicalField) {
        JsonNode canonical = source.get(canonicalField);
        return canonical == null ? source.get(pascalCase(canonicalField)) : canonical;
    }

    private String pascalCase(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private JsonNode normalizeError(RobotActionEvent.State state, JsonNode info) {
        String rawClientCode = info.path("ClientCode").asText(null);
        JsonNode deviceFault = normalizeDeviceFault(info.get("DeviceFault"), info.get("ErrorEvent"));
        boolean terminalFailure = state == RobotActionEvent.State.REJECTED
                || state == RobotActionEvent.State.FAILED || state == RobotActionEvent.State.UNKNOWN;
        if (!terminalFailure && (rawClientCode == null || rawClientCode.trim().isEmpty())
                && deviceFault == null) {
            return null;
        }

        ObjectNode error = objectMapper.createObjectNode();
        Integer clientCode = clientCodeMapper.map(rawClientCode);
        if (clientCode != null) error.put("clientCode", clientCode);
        putText(error, "rawClientCode", rawClientCode);
        putText(error, "message", info.path("Message").asText(null));
        if (deviceFault != null) error.set("deviceFault", deviceFault);
        // 规范字段用于业务映射，原始 MessageInfo 用于联调和厂家问题追溯。
        error.set("rawMessageInfo", info.deepCopy());
        return error;
    }

    private JsonNode normalizeDeviceFault(JsonNode source, JsonNode fallbackErrorEvent) {
        if (source != null && source.isObject()) {
            ObjectNode target = objectMapper.createObjectNode();
            putText(target, "vendor", source.path("Vendor").asText(null));
            putText(target, "deviceType", source.path("DeviceType").asText(null));
            putText(target, "code", source.path("RawCode").asText(null));
            putText(target, "message", source.path("RawMessage").asText(null));
            putText(target, "operation", source.path("Operation").asText(null));
            return target;
        }
        if (fallbackErrorEvent != null && fallbackErrorEvent.isObject()) {
            ObjectNode target = objectMapper.createObjectNode();
            putText(target, "vendor", fallbackErrorEvent.path("DeviceVendor").asText(null));
            putText(target, "deviceType", fallbackErrorEvent.path("Device").asText(null));
            putText(target, "code", fallbackErrorEvent.path("DeviceErrorCode").asText(null));
            putText(target, "message", fallbackErrorEvent.path("DeviceErrorMessage").asText(null));
            putText(target, "operation", fallbackErrorEvent.path("SubAction").asText(null));
            return target;
        }
        return null;
    }

    private RobotActionEvent.State normalizeState(String rawState, PhysicalOutcome outcome) {
        if ("ACCEPTED".equals(rawState)) return RobotActionEvent.State.ACCEPTED;
        if ("RUNNING".equals(rawState)) return RobotActionEvent.State.RUNNING;
        if ("FINISHED".equals(rawState)) return RobotActionEvent.State.FINISHED;
        if ("REJECTED".equals(rawState)) return RobotActionEvent.State.REJECTED;
        if ("FAILED".equals(rawState)) return RobotActionEvent.State.FAILED;
        if (!"UNKNOWN_HOLD".equals(rawState)) {
            throw new IllegalArgumentException("不支持的 cnet8 动作状态：" + rawState);
        }
        if (outcome == PhysicalOutcome.UNKNOWN) return RobotActionEvent.State.UNKNOWN;
        if (outcome == PhysicalOutcome.PARTIALLY_COMPLETED
                || outcome == PhysicalOutcome.CONFIRMED_FAILED) {
            return RobotActionEvent.State.FAILED;
        }
        if (outcome == PhysicalOutcome.NOT_STARTED) return RobotActionEvent.State.REJECTED;
        throw new IllegalArgumentException("UNKNOWN_HOLD 不能携带 physicalOutcome=" + outcome);
    }

    private PhysicalOutcome parsePhysicalOutcome(String value) {
        try {
            return PhysicalOutcome.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 cnet8 physicalOutcome：" + value, exception);
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("cnet8 时间戳必须是带时区的 ISO-8601 字符串：" + value,
                    exception);
        }
    }

    private String consistentIdentity(JsonNode envelope,
                                      JsonNode info,
                                      String field,
                                      int maximumLength) {
        String infoValue = requiredText(info, field, maximumLength);
        JsonNode envelopeValue = envelope.get(field);
        if (envelopeValue != null && envelopeValue.isTextual()
                && !envelopeValue.textValue().trim().isEmpty()
                && !infoValue.equals(envelopeValue.textValue())) {
            throw new IllegalArgumentException("cnet8 信封与 MessageInfo 的 " + field + " 不一致");
        }
        return infoValue;
    }

    private void requireTextEquals(JsonNode parent, String field, String expected) {
        String actual = requiredText(parent, field);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + " 与当前会话不一致：" + actual);
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

    private void putRequiredText(ObjectNode target, String targetField, JsonNode source, String sourceField) {
        target.put(targetField, requiredText(source, sourceField));
    }

    private void putText(ObjectNode target, String field, String value) {
        if (value != null && !value.trim().isEmpty()) target.put(field, value);
    }
}

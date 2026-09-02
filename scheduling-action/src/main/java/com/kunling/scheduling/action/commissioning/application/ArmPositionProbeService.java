package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.execution.application.ActionExecutionStore;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 下发单步骤、只读的 MOVE_TO_POSE 查询命令并提取当前位姿。 */
@Service
public class ArmPositionProbeService {
    static final String MOVE_TO_POSE = "MOVE_TO_POSE";
    static final int MAX_PROBE_TIMEOUT_MS = 10_000;
    private static final int RESPONSE_GRACE_MS = 1_000;

    private final RobotActionTransport transport;
    private final ActionExecutionStore executionStore;
    private final ArmPositionProbeCoordinator coordinator;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ArmPositionProbeService(RobotActionTransport transport,
                                   ActionExecutionStore executionStore,
                                   ArmPositionProbeCoordinator coordinator,
                                   ObjectMapper objectMapper,
                                   JsonCodec jsonCodec) {
        this(transport, executionStore, coordinator, objectMapper, jsonCodec, Clock.systemUTC());
    }

    ArmPositionProbeService(RobotActionTransport transport,
                            ActionExecutionStore executionStore,
                            ArmPositionProbeCoordinator coordinator,
                            ObjectMapper objectMapper,
                            JsonCodec jsonCodec,
                            Clock clock) {
        this.transport = transport;
        this.executionStore = executionStore;
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    public ArmPositionProbeResult probe(String robotId) {
        String normalizedRobotId = requireRobotId(robotId);
        if (executionStore.hasActiveExecutionForRobot(normalizedRobotId)) {
            throw new ActionConflictException("机器人正在执行其他 Action，禁止发起位置探测。");
        }
        RobotSessionView session = transport.findSession(normalizedRobotId)
                .orElseThrow(() -> new RobotUnavailableException("机器人未在线：" + normalizedRobotId));
        RobotOperationCapability capability = findMoveCapability(session);
        int timeoutMs = resolveTimeout(capability);
        RobotActionCommand command = createProbeCommand(normalizedRobotId, timeoutMs);

        try (ArmPositionProbeCoordinator.ProbeTicket ticket =
                     coordinator.register(normalizedRobotId, command.actionInstanceId(),
                             command.deviceCommandId())) {
            transport.dispatch(command);
            RobotActionEvent event = ticket.await(Duration.ofMillis(timeoutMs + RESPONSE_GRACE_MS));
            return extractResult(normalizedRobotId, event);
        }
    }

    RobotActionCommand createProbeCommand(String robotId, int timeoutMs) {
        Instant now = clock.instant();
        String random = UUID.randomUUID().toString().replace("-", "");
        String actionInstanceId = "arm-position-probe-" + random;
        String deviceCommandId = random;

        ObjectNode params = objectMapper.createObjectNode();
        params.put("commandId", deviceCommandId);
        params.put("armCommandModelType", 3);
        params.putNull("armCommandInfo");
        ObjectNode request = params.putObject("armMoveRequestParams");
        request.put("armMoveRequestType", 1);
        request.put("speedPercent", 10);
        putZeroCartesian(request.putObject("armPoseXYZRxRyRz"));
        putZeroJoints(request.putObject("armPoseJ1J2J3J4J5J6"));

        ObjectNode step = objectMapper.createObjectNode();
        step.put("stepId", "query-arm-position");
        step.put("operation", MOVE_TO_POSE);
        step.set("params", params);
        step.put("gate", true);
        ObjectNode failure = step.putObject("onFailure");
        failure.putArray("rules");
        ObjectNode defaultDirective = failure.putObject("default");
        defaultDirective.put("action", "STOP_AND_REPORT");

        ArrayNode steps = objectMapper.createArrayNode().add(step);
        ObjectNode input = objectMapper.createObjectNode();
        input.putObject("executionPlan").set("steps", steps);
        String packageHash = jsonCodec.sha256(jsonCodec.writeCanonical(input));
        return new RobotActionCommand(robotId, actionInstanceId, deviceCommandId,
                "2.0", packageHash, input, timeoutMs, now);
    }

    ArmPositionProbeResult extractResult(String robotId, RobotActionEvent event) {
        if (event.state() != RobotActionEvent.State.FINISHED) {
            String message = event.error() == null ? null : event.error().path("message").asText(null);
            throw new RobotUnavailableException(message == null || message.trim().isEmpty()
                    ? "机械臂位置探测未返回成功终态：" + event.state()
                    : "机械臂位置探测失败：" + message);
        }
        JsonNode resultData = locateResultData(event);
        if (resultData == null || !resultData.isObject()) {
            throw new RobotUnavailableException("机械臂位置探测结果缺少 resultData。");
        }
        JsonNode cartesian = requiredObject(resultData, "armPoseXYZRxRyRz");
        JsonNode joints = requiredObject(resultData, "armPoseJ1J2J3J4J5J6");
        return new ArmPositionProbeResult(
                robotId,
                event.timestamp() == null ? clock.instant() : event.timestamp(),
                requiredInt(resultData, "armMoveRequestType"),
                requiredInt(resultData, "speedPercent"),
                new ArmPositionProbeResult.CartesianPose(
                        requiredNumber(cartesian, "x"), requiredNumber(cartesian, "y"),
                        requiredNumber(cartesian, "z"), requiredNumber(cartesian, "rx"),
                        requiredNumber(cartesian, "ry"), requiredNumber(cartesian, "rz")),
                new ArmPositionProbeResult.JointPose(
                        requiredNumber(joints, "j1"), requiredNumber(joints, "j2"),
                        requiredNumber(joints, "j3"), requiredNumber(joints, "j4"),
                        requiredNumber(joints, "j5"), requiredNumber(joints, "j6")));
    }

    private RobotOperationCapability findMoveCapability(RobotSessionView session) {
        RobotOperationCapability direct = session.operationCapabilities().get(MOVE_TO_POSE);
        if (direct != null) return direct;
        for (Map.Entry<String, RobotOperationCapability> entry : session.operationCapabilities().entrySet()) {
            if (entry.getValue() != null && MOVE_TO_POSE.equals(entry.getValue().operation())) {
                return entry.getValue();
            }
        }
        throw new RobotUnavailableException("机器人未注册 MOVE_TO_POSE 能力：" + session.robotId());
    }

    private int resolveTimeout(RobotOperationCapability capability) {
        if (capability.minTimeoutMs() > MAX_PROBE_TIMEOUT_MS || capability.maxTimeoutMs() < 1
                || capability.minTimeoutMs() > capability.maxTimeoutMs()) {
            throw new RobotUnavailableException("MOVE_TO_POSE 能力的超时范围不支持调试探测。");
        }
        return Math.max(capability.minTimeoutMs(), Math.min(MAX_PROBE_TIMEOUT_MS,
                capability.maxTimeoutMs()));
    }

    private JsonNode locateResultData(RobotActionEvent event) {
        JsonNode steps = event.resolvedSteps();
        if (steps != null && steps.isArray()) {
            for (JsonNode step : steps) {
                if (MOVE_TO_POSE.equals(step.path("operation").asText())
                        && step.path("success").asBoolean(false)
                        && step.path("resultData").isObject()) {
                    return step.path("resultData");
                }
            }
        }
        JsonNode step = event.stepEvent();
        if (step != null && "SUCCEEDED".equals(step.path("stepState").asText())
                && step.path("resultData").isObject()) {
            return step.path("resultData");
        }
        return null;
    }

    private JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new RobotUnavailableException("机械臂位置探测结果缺少对象字段：" + field);
        }
        return value;
    }

    private int requiredInt(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new RobotUnavailableException("机械臂位置探测结果字段必须是整数：" + field);
        }
        return value.intValue();
    }

    private double requiredNumber(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isNumber()) {
            throw new RobotUnavailableException("机械臂位置探测结果字段必须是数字：" + field);
        }
        return value.doubleValue();
    }

    private String requireRobotId(String robotId) {
        if (robotId == null || robotId.trim().isEmpty()) {
            throw new IllegalArgumentException("robotId 不能为空。");
        }
        return robotId.trim();
    }

    private void putZeroCartesian(ObjectNode pose) {
        for (String field : new String[]{"x", "y", "z", "rx", "ry", "rz"}) pose.put(field, 0.0D);
    }

    private void putZeroJoints(ObjectNode pose) {
        for (String field : new String[]{"j1", "j2", "j3", "j4", "j5", "j6"}) pose.put(field, 0.0D);
    }
}

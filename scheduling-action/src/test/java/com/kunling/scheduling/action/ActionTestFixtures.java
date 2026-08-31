package com.kunling.scheduling.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import com.kunling.scheduling.action.definition.domain.ActionFailurePolicy;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionTestFixtures {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private ActionTestFixtures() {
    }

    public static ActionStepDefinition step(String stepId, String operation, boolean gate,
                                            ActionFailurePolicy policy) {
        return new ActionStepDefinition(stepId, operation,
                JsonNodeFactory.instance.objectNode(), gate, policy);
    }

    public static ActionDefinition definition(String id, boolean enabled,
                                              ActionStepDefinition... steps) {
        return new ActionDefinition(id, "移动到目标点", enabled, 60_000,
                ImmutableCollections.copyList(java.util.Arrays.asList(steps)));
    }

    public static ActionDefinition definition(String id, boolean enabled) {
        return definition(id, enabled,
                step("move", "MOVE_TO_MAP_POINT", true, ActionFailurePolicy.stopAndReport()));
    }

    public static ActionFailureDirective retry() {
        return new ActionFailureDirective(ActionFailureDirectiveType.RETRY_STEP,
                2, 100, null, null, ActionFailureDirectiveType.STOP_AND_REPORT);
    }

    public static RobotSessionView session() {
        Map<String, RobotOperationCapability> capabilities =
                new LinkedHashMap<String, RobotOperationCapability>();
        capabilities.put("MOVE_TO_MAP_POINT",
                new RobotOperationCapability("MOVE_TO_MAP_POINT", 1_000, 300_000));
        capabilities.put("CHASSIS_VERIFY_STOPPED",
                new RobotOperationCapability("CHASSIS_VERIFY_STOPPED", 1_000, 300_000));
        return new RobotSessionView("session-1", "R01", "COMPOSITE", "client-1",
                capabilities, ImmutableCollections.copySet(ImmutableCollections.listOf(
                "RETRY_STEP", "VERIFY_THEN_RETRY", "SKIP_STEP", "STOP_AND_REPORT")),
                Instant.EPOCH, Instant.EPOCH);
    }

    public static NewActionExecution newExecution() {
        return new NewActionExecution("action-1", "definition-1", "R01", "dc-1",
                "2.0", "request-hash", "package-hash",
                executionPlan(), 60_000, Instant.EPOCH);
    }

    public static JsonNode executionPlan() {
        return MAPPER.createObjectNode().set("executionPlan",
                MAPPER.createObjectNode().set("steps", MAPPER.createArrayNode().add(
                        MAPPER.createObjectNode().put("stepId", "move")
                                .put("operation", "MOVE_TO_MAP_POINT")
                                .set("params", MAPPER.createObjectNode()))));
    }

    public static ActionExecutionView execution(ActionExecutionState state,
                                                PhysicalOutcome outcome, JsonNode error) {
        JsonNode step = MAPPER.createObjectNode().put("stepId", "move")
                .put("operation", "MOVE_TO_MAP_POINT");
        return new ActionExecutionView("action-1", "definition-1", "R01", "dc-1",
                "2.0", "request-hash", "package-hash", state, outcome, 60_000,
                executionPlan(), step, MAPPER.createArrayNode(), error,
                "session-1", "message-1", "event-1", "session-1", 1L,
                Instant.EPOCH, Instant.EPOCH, state.terminal() ? Instant.EPOCH : null);
    }
}

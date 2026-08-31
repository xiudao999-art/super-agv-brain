package com.kunling.scheduling.action.robotbridge;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import com.kunling.scheduling.action.definition.domain.ActionFailurePolicy;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionCapabilityValidatorTest {
    private final ActionCapabilityValidator validator = new ActionCapabilityValidator();

    @Test
    void validatesOperationTimeoutAndPolicyWithoutSchemaHash() {
        assertThatCode(() -> validator.validate(
                ActionTestFixtures.definition("definition-1", true), ActionTestFixtures.session()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnregisteredOperation() {
        ActionDefinition definition = ActionTestFixtures.definition("definition-1", true,
                ActionTestFixtures.step("arm", "ARM.MOVE", true,
                        com.kunling.scheduling.action.definition.domain.ActionFailurePolicy.stopAndReport()));
        assertThatThrownBy(() -> validator.validate(definition, ActionTestFixtures.session()))
                .hasMessageContaining("未注册原子操作");
    }

    @Test
    void rejectsTimeoutOutsideRegisteredRange() {
        Map<String, RobotOperationCapability> capabilities =
                new LinkedHashMap<String, RobotOperationCapability>();
        capabilities.put("MOVE_TO_MAP_POINT",
                new RobotOperationCapability("MOVE_TO_MAP_POINT", 1_000, 30_000));
        RobotSessionView session = new RobotSessionView("session-1", "R01", "COMPOSITE", "client-1",
                capabilities, ImmutableCollections.copySet(
                ImmutableCollections.listOf("STOP_AND_REPORT")), Instant.EPOCH, Instant.EPOCH);
        assertThatThrownBy(() -> validator.validate(
                ActionTestFixtures.definition("definition-1", true), session))
                .hasMessageContaining("超出操作").hasMessageContaining("30000");
    }

    @Test
    void alsoValidatesTimeoutRangeOfVerifyOperation() {
        ActionFailureDirective verify = new ActionFailureDirective(
                ActionFailureDirectiveType.VERIFY_THEN_RETRY, 1, 0,
                "CHASSIS_VERIFY_STOPPED", ActionTestFixtures.MAPPER.createObjectNode(),
                ActionFailureDirectiveType.STOP_AND_REPORT);
        ActionFailurePolicy policy = new ActionFailurePolicy(ImmutableCollections.listOf(), verify);
        ActionStepDefinition step = ActionTestFixtures.step(
                "move", "MOVE_TO_MAP_POINT", true, policy);

        Map<String, RobotOperationCapability> capabilities =
                new LinkedHashMap<String, RobotOperationCapability>();
        capabilities.put("MOVE_TO_MAP_POINT",
                new RobotOperationCapability("MOVE_TO_MAP_POINT", 1_000, 300_000));
        capabilities.put("CHASSIS_VERIFY_STOPPED",
                new RobotOperationCapability("CHASSIS_VERIFY_STOPPED", 1_000, 30_000));
        RobotSessionView session = new RobotSessionView("session-1", "R01", "COMPOSITE", "client-1",
                capabilities, ImmutableCollections.copySet(ImmutableCollections.listOf(
                "VERIFY_THEN_RETRY", "STOP_AND_REPORT")), Instant.EPOCH, Instant.EPOCH);

        assertThatThrownBy(() -> validator.validate(
                ActionTestFixtures.definition("definition-1", true, step), session))
                .hasMessageContaining("CHASSIS_VERIFY_STOPPED").hasMessageContaining("30000");
    }
}

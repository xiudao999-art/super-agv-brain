package com.kunling.scheduling.action.definition;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.definition.application.ActionDefinitionValidator;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import com.kunling.scheduling.action.definition.domain.ActionFailurePolicy;
import com.kunling.scheduling.action.definition.domain.ActionFailureRule;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionDefinitionValidatorTest {
    private final ActionDefinitionValidator validator = new ActionDefinitionValidator();

    @Test
    void acceptsMinimalDefinitionWithoutSchema() {
        assertThatCode(() -> validator.validateExecutable(
                ActionTestFixtures.definition("definition-1", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateStepId() {
        ActionStepDefinition first = ActionTestFixtures.step(
                "move", "MOVE_TO_MAP_POINT", false, ActionFailurePolicy.stopAndReport());
        ActionStepDefinition second = ActionTestFixtures.step(
                "move", "MOVE_TO_MAP_POINT", false, ActionFailurePolicy.stopAndReport());
        assertThatThrownBy(() -> validator.validateExecutable(
                ActionTestFixtures.definition("definition-1", false, first, second)))
                .hasMessageContaining("stepId 重复");
    }

    @Test
    void rejectsNonObjectParams() {
        ActionStepDefinition step = new ActionStepDefinition("move", "MOVE_TO_MAP_POINT",
                JsonNodeFactory.instance.arrayNode(), false, ActionFailurePolicy.stopAndReport());
        assertThatThrownBy(() -> validator.validateExecutable(definition(step)))
                .hasMessageContaining("params 必须是 JSON 对象");

        ActionStepDefinition missingParams = new ActionStepDefinition(
                "move", "MOVE_TO_MAP_POINT", null, false, ActionFailurePolicy.stopAndReport());
        assertThatThrownBy(() -> validator.validateExecutable(definition(missingParams)))
                .hasMessageContaining("params 必须是 JSON 对象");
    }

    @Test
    void gateStepCannotEndInSkip() {
        ActionStepDefinition step = ActionTestFixtures.step("move", "MOVE_TO_MAP_POINT", true,
                new ActionFailurePolicy(ImmutableCollections.listOf(), ActionFailureDirective.skipStep()));
        assertThatThrownBy(() -> validator.validateExecutable(definition(step)))
                .hasMessageContaining("门禁 step").hasMessageContaining("SKIP_STEP");
    }

    @Test
    void retryRequiresPositiveRetriesAndExhaustStrategy() {
        ActionFailureDirective invalid = new ActionFailureDirective(
                ActionFailureDirectiveType.RETRY_STEP, 0, 0, null, null, null);
        ActionStepDefinition step = ActionTestFixtures.step("move", "MOVE_TO_MAP_POINT", false,
                new ActionFailurePolicy(ImmutableCollections.listOf(), invalid));
        assertThatThrownBy(() -> validator.validateExecutable(definition(step)))
                .hasMessageContaining("maxRetries 必须大于 0");
    }

    @Test
    void verifyParamsMustBeObject() {
        ActionFailureDirective invalid = new ActionFailureDirective(
                ActionFailureDirectiveType.VERIFY_THEN_RETRY, 1, 0,
                "CHASSIS_VERIFY_STOPPED", JsonNodeFactory.instance.arrayNode(),
                ActionFailureDirectiveType.STOP_AND_REPORT);
        ActionStepDefinition step = ActionTestFixtures.step("move", "MOVE_TO_MAP_POINT", false,
                new ActionFailurePolicy(ImmutableCollections.listOf(), invalid));
        assertThatThrownBy(() -> validator.validateExecutable(definition(step)))
                .hasMessageContaining("verifyParams 必须是 JSON 对象");
    }

    @Test
    void requiresFailurePolicyAndDefaultDirective() {
        ActionStepDefinition withoutPolicy = new ActionStepDefinition(
                "move", "MOVE_TO_MAP_POINT", JsonNodeFactory.instance.objectNode(), false, null);
        assertThatThrownBy(() -> validator.validateExecutable(definition(withoutPolicy)))
                .hasMessageContaining("onFailure.defaultDirective 不能为空");
    }

    @Test
    void failureRulesNeedReasonCodeButNotPolicyId() {
        ActionFailureRule rule = new ActionFailureRule(" ", ActionFailureDirective.stopAndReport());
        ActionStepDefinition step = ActionTestFixtures.step("move", "MOVE_TO_MAP_POINT", false,
                new ActionFailurePolicy(ImmutableCollections.listOf(rule),
                        ActionFailureDirective.stopAndReport()));
        assertThatThrownBy(() -> validator.validateExecutable(definition(step)))
                .hasMessageContaining("reasonCode");
    }

    private ActionDefinition definition(ActionStepDefinition step) {
        return ActionTestFixtures.definition("definition-1", false, step);
    }
}

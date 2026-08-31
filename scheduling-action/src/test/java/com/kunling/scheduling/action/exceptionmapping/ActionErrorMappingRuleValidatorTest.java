package com.kunling.scheduling.action.exceptionmapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleValidator;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleResult;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionErrorMappingRuleValidatorTest {
    private final ActionErrorMappingRuleValidator validator = new ActionErrorMappingRuleValidator();

    @Test
    void requiresVendorDeviceTypeAndExactRawCode() {
        ActionErrorMappingRule rule = rule(new ErrorMappingRuleMatch(null, "", "CHASSIS", "NAV_TIMEOUT"));
        assertThatThrownBy(() -> validator.validate(rule)).hasMessageContaining("vendor");
    }

    @Test
    void vendorAndDeviceTypeMustUseStableUppercaseIdentifiers() {
        ActionErrorMappingRule rule = rule(new ErrorMappingRuleMatch(
                null, "hikrobot", "CHASSIS", "NAV_TIMEOUT"));

        assertThatThrownBy(() -> validator.validate(rule))
                .hasMessageContaining("vendor", "大写标识");
    }

    @Test
    void roundTripKeepsOnlyTheMinimalMappingDimensions() {
        ActionErrorMappingRule rule = rule(new ErrorMappingRuleMatch(
                "MOVE_TO_MAP_POINT", "HIKROBOT", "CHASSIS", "NAV_TIMEOUT"));
        JsonCodec jsonCodec = new JsonCodec(new ObjectMapper().findAndRegisterModules());

        ActionErrorMappingRule restored = jsonCodec.read(jsonCodec.write(rule), ActionErrorMappingRule.class);

        assertThat(restored).isEqualTo(rule);
        assertThat(jsonCodec.write(restored)).contains("operation", "vendor", "deviceType", "rawCode")
                .doesNotContain("physicalOutcome", "policy", "model", "adapter");
    }

    private ActionErrorMappingRule rule(ErrorMappingRuleMatch match) {
        return new ActionErrorMappingRule("HIK-MOVE", "HIK-CHASSIS", 1000, match,
                new ErrorMappingRuleResult("3001", "导航阻塞超时", "MOVE.OBSTACLE_TIMEOUT",
                        HandlingConstraint.RETRYABLE, "清障后重试"));
    }
}

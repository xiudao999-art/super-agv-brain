package com.kunling.scheduling.action.exceptionmapping;

import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorDecision;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorMappingEngine;
import com.kunling.scheduling.action.exceptionmapping.application.ErrorMappingContext;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleResult;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessErrorMappingEngineTest {
    private final BusinessErrorMappingEngine engine = new BusinessErrorMappingEngine();

    @Test
    void mapsVendorDeviceTypeAndRawCodeExactly() {
        BusinessErrorDecision decision = engine.resolve(ImmutableCollections.listOf(rule()),
                context("MOVE_TO_MAP_POINT", "NAV_TIMEOUT"));

        assertThat(decision.businessCode()).isEqualTo("3001");
        assertThat(decision.handlingConstraint()).isEqualTo(HandlingConstraint.RETRYABLE);
    }

    @Test
    void operationIsOnlyAnOptionalDisambiguator() {
        assertThat(engine.resolve(ImmutableCollections.listOf(rule()),
                context("OTHER_OPERATION", "NAV_TIMEOUT")).businessCode()).isEqualTo("3001");
    }

    @Test
    void rawCodeDoesNotTrimOrIgnoreCase() {
        assertThat(engine.resolve(ImmutableCollections.listOf(rule()),
                context("MOVE_TO_MAP_POINT", "nav_timeout")).businessCode()).isEqualTo("5999");
    }

    @Test
    void unmatchedFaultUsesManualFallback() {
        BusinessErrorDecision decision = engine.resolve(ImmutableCollections.listOf(),
                context(null, "UNKNOWN"));
        assertThat(decision.businessCode()).isEqualTo("5999");
        assertThat(decision.handlingConstraint()).isEqualTo(HandlingConstraint.MANUAL_INTERVENTION);
    }

    private ActionErrorMappingRule rule() {
        return new ActionErrorMappingRule("rule-1", "HIK", 100,
                new ErrorMappingRuleMatch(null, "HIKROBOT", "CHASSIS", "NAV_TIMEOUT"),
                new ErrorMappingRuleResult("3001", "导航阻塞超时", "MOVE.OBSTACLE_TIMEOUT",
                        HandlingConstraint.RETRYABLE, null));
    }

    private ErrorMappingContext context(String operation, String code) {
        return new ErrorMappingContext("move", operation, "HIKROBOT", "CHASSIS", code, "障碍物");
    }
}

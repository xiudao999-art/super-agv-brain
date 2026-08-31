package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleService;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorMappingEngine;
import com.kunling.scheduling.action.exceptionmapping.application.ClientFaultCatalog;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleResult;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActionExecutionReportMapperTest {
    private final ActionErrorMappingRuleService ruleService = mock(ActionErrorMappingRuleService.class);
    private final ActionExecutionReportMapper mapper = new ActionExecutionReportMapper(
            new BusinessErrorMappingEngine(), ruleService, new ClientFaultCatalog());

    @Test
    void finishedMapsToMinimalSuccessReport() {
        ActionExecutionReport report = mapper.fromLocalState(ActionTestFixtures.execution(
                ActionExecutionState.FINISHED, PhysicalOutcome.CONFIRMED_SUCCEEDED, null),
                java.time.Instant.EPOCH);
        assertThat(report.result()).isEqualTo(ActionExecutionResult.SUCCEEDED);
        assertThat(report.failure()).isNull();
    }

    @Test
    void rejectedClientInputMapsToFailedNotStarted() {
        JsonNode error = ActionTestFixtures.MAPPER.createObjectNode()
                .put("clientCode", 40202).put("message", "参数错误");
        ActionExecutionReport report = mapper.fromLocalState(ActionTestFixtures.execution(
                ActionExecutionState.REJECTED, PhysicalOutcome.NOT_STARTED, error),
                java.time.Instant.EPOCH);
        assertThat(report.result()).isEqualTo(ActionExecutionResult.FAILED);
        assertThat(report.failure().businessCode()).isEqualTo("ACTION.CLIENT.INVALID_INPUT");
    }

    @Test
    void deviceFaultUsesCurrentMappingAndMinimalDeviceFields() {
        when(ruleService.activeRules()).thenReturn(ImmutableCollections.listOf(mappingRule()));
        JsonNode error = ActionTestFixtures.MAPPER.createObjectNode()
                .put("clientCode", 50203).put("message", "设备失败")
                .set("deviceFault", ActionTestFixtures.MAPPER.createObjectNode()
                        .put("vendor", "HIKROBOT").put("deviceType", "CHASSIS")
                        .put("model", "Q7").put("deviceId", "R01-CHASSIS")
                        .put("code", "NAV_TIMEOUT").put("message", "障碍持续"));
        ActionExecutionReport report = mapper.fromLocalState(ActionTestFixtures.execution(
                ActionExecutionState.FAILED, PhysicalOutcome.CONFIRMED_FAILED, error),
                java.time.Instant.EPOCH);
        assertThat(report.failure().businessCode()).isEqualTo("3001");
        assertThat(report.failure().deviceFault().code()).isEqualTo("NAV_TIMEOUT");
    }

    @Test
    void unknownHoldAlwaysRequiresManualIntervention() {
        JsonNode error = ActionTestFixtures.MAPPER.createObjectNode()
                .put("message", "连接中断");
        ActionExecutionReport report = mapper.fromLocalState(ActionTestFixtures.execution(
                ActionExecutionState.UNKNOWN_HOLD, PhysicalOutcome.UNKNOWN, error),
                java.time.Instant.EPOCH);
        assertThat(report.result()).isEqualTo(ActionExecutionResult.UNKNOWN_HOLD);
        assertThat(report.failure().handlingConstraint())
                .isEqualTo(HandlingConstraint.MANUAL_INTERVENTION);
    }

    private ActionErrorMappingRule mappingRule() {
        return new ActionErrorMappingRule("rule-1", "HIK", 100,
                new ErrorMappingRuleMatch("MOVE_TO_MAP_POINT", "HIKROBOT", "CHASSIS", "NAV_TIMEOUT"),
                new ErrorMappingRuleResult("3001", "导航阻塞超时", "MOVE.OBSTACLE_TIMEOUT",
                        HandlingConstraint.RETRYABLE, null));
    }
}

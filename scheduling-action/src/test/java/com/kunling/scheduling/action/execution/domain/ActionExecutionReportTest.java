package com.kunling.scheduling.action.execution.domain;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionExecutionReportTest {
    private final JsonCodec codec = new JsonCodec(ActionTestFixtures.MAPPER);

    @Test
    void serializationContainsOnlyMinimalCrossModuleFields() {
        ActionExecutionReport report = new ActionExecutionReport("action-1",
                ActionExecutionResult.FAILED, PhysicalOutcome.NOT_STARTED,
                new ActionExecutionReport.Failure("move", "ACTION.CLIENT.INVALID_INPUT",
                        HandlingConstraint.NON_RETRYABLE, "参数错误", null));
        String json = codec.write(report);

        assertThat(json).contains("actionInstanceId", "result", "physicalOutcome", "failure")
                .doesNotContain("workflow", "actionKey", "robotId", "completedAt", "reasonCode",
                        "handlingAdvice", "rawPayload", "operation");
    }

    @Test
    void uncertainOutcomeCannotRemainRetryable() {
        ActionExecutionReport report = new ActionExecutionReport("action-1",
                ActionExecutionResult.UNKNOWN_HOLD, PhysicalOutcome.UNKNOWN,
                new ActionExecutionReport.Failure("move", "3001", HandlingConstraint.RETRYABLE,
                        "结果未知", null));
        assertThat(report.failure().handlingConstraint())
                .isEqualTo(HandlingConstraint.MANUAL_INTERVENTION);
    }

    @Test
    void successCannotCarryFailure() {
        assertThatThrownBy(() -> new ActionExecutionReport("action-1",
                ActionExecutionResult.SUCCEEDED, PhysicalOutcome.CONFIRMED_SUCCEEDED,
                new ActionExecutionReport.Failure("move", "3001", HandlingConstraint.RETRYABLE,
                        "不应存在", null))).hasMessageContaining("成功报告不能包含");
    }
}

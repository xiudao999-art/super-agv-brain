package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionReportEventResolverTest {

    private final ActionReportEventResolver resolver = new ActionReportEventResolver();

    @Test
    void resolvesSuccessfulReport() {
        ActionExecutionReport report = new ActionExecutionReport(
                "action-1", ActionExecutionResult.SUCCEEDED,
                PhysicalOutcome.CONFIRMED_SUCCEEDED, null);

        assertEquals("SUCCEEDED", resolver.resolve(report));
    }

    @Test
    void resolvesFailureByHandlingConstraint() {
        ActionExecutionReport report = failedReport(
                ActionExecutionResult.FAILED,
                PhysicalOutcome.CONFIRMED_FAILED,
                HandlingConstraint.RETRYABLE);

        assertEquals("RETRYABLE", resolver.resolve(report));
    }

    @Test
    void unknownHoldAlwaysRequiresManualIntervention() {
        ActionExecutionReport report = failedReport(
                ActionExecutionResult.UNKNOWN_HOLD,
                PhysicalOutcome.UNKNOWN,
                HandlingConstraint.RETRYABLE);

        assertEquals("MANUAL_INTERVENTION", resolver.resolve(report));
    }

    @Test
    void partiallyCompletedActionCannotBeRetriedAutomatically() {
        ActionExecutionReport report = failedReport(
                ActionExecutionResult.FAILED,
                PhysicalOutcome.PARTIALLY_COMPLETED,
                HandlingConstraint.RETRYABLE);

        assertEquals("MANUAL_INTERVENTION", resolver.resolve(report));
    }

    private ActionExecutionReport failedReport(ActionExecutionResult result,
                                               PhysicalOutcome outcome,
                                               HandlingConstraint constraint) {
        ActionExecutionReport.Failure failure = new ActionExecutionReport.Failure(
                "move", "3001", constraint, "移动失败", null);
        return new ActionExecutionReport("action-1", result, outcome, failure);
    }
}

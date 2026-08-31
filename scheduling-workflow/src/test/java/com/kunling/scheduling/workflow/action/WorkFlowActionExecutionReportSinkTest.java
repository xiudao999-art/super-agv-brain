package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import com.kunling.scheduling.workflow.service.NodeStateTransitionRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkFlowActionExecutionReportSinkTest {

    @Test
    void forwardsMinimalReportByActionInstanceId() {
        NodeStateTransitionRuleService transitionService = mock(NodeStateTransitionRuleService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NodeStateTransitionRuleService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(transitionService);
        WorkFlowActionExecutionReportSink sink = new WorkFlowActionExecutionReportSink(provider);
        ActionExecutionReport report = failedReport();

        sink.accept(report);

        verify(transitionService).statusChanged(same(report));
    }

    @Test
    void ignoresNullReportWithoutCallingStateMachine() {
        NodeStateTransitionRuleService transitionService = mock(NodeStateTransitionRuleService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NodeStateTransitionRuleService> provider = mock(ObjectProvider.class);
        WorkFlowActionExecutionReportSink sink = new WorkFlowActionExecutionReportSink(provider);

        sink.accept(null);

        verify(transitionService, never()).statusChanged(org.mockito.ArgumentMatchers.any());
    }

    private ActionExecutionReport failedReport() {
        ActionExecutionReport.Failure failure = new ActionExecutionReport.Failure(
                "move", "3001", HandlingConstraint.MANUAL_INTERVENTION,
                "移动失败", new ActionExecutionReport.DeviceFault(
                "HIKROBOT", "CHASSIS", "NAV_TIMEOUT", "导航超时"));
        return new ActionExecutionReport("action-1", ActionExecutionResult.FAILED,
                PhysicalOutcome.CONFIRMED_FAILED, failure);
    }
}

package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** Action 模块向执行引擎交付的最小最终事实。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionReport {
    String actionInstanceId;
    ActionExecutionResult result;
    PhysicalOutcome physicalOutcome;
    Failure failure;

    @ConstructorProperties({"actionInstanceId", "result", "physicalOutcome", "failure"})
    public ActionExecutionReport(String actionInstanceId, ActionExecutionResult result,
                                 PhysicalOutcome physicalOutcome, Failure failure) {
        requireText(actionInstanceId, "actionInstanceId");
        if (result == null) throw new IllegalArgumentException("result 不能为空。");
        if (physicalOutcome == null) throw new IllegalArgumentException("physicalOutcome 不能为空。");
        if (result == ActionExecutionResult.SUCCEEDED && failure != null) {
            throw new IllegalArgumentException("成功报告不能包含 failure。");
        }
        if (result != ActionExecutionResult.SUCCEEDED && failure == null) {
            throw new IllegalArgumentException("非成功报告必须包含 failure。");
        }
        if (result == ActionExecutionResult.SUCCEEDED
                && physicalOutcome != PhysicalOutcome.CONFIRMED_SUCCEEDED) {
            throw new IllegalArgumentException("成功报告必须是 CONFIRMED_SUCCEEDED。");
        }
        this.actionInstanceId = actionInstanceId;
        this.result = result;
        this.physicalOutcome = physicalOutcome;
        this.failure = failure == null ? null : constrain(failure, physicalOutcome);
    }

    private static Failure constrain(Failure failure, PhysicalOutcome outcome) {
        if (failure.handlingConstraint() == HandlingConstraint.RETRYABLE
                && (outcome == PhysicalOutcome.UNKNOWN || outcome == PhysicalOutcome.PARTIALLY_COMPLETED)) {
            return failure.withHandlingConstraint(HandlingConstraint.MANUAL_INTERVENTION);
        }
        return failure;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Failure {
        String stepId;
        String businessCode;
        HandlingConstraint handlingConstraint;
        String message;
        DeviceFault deviceFault;

        @ConstructorProperties({"stepId", "businessCode", "handlingConstraint", "message", "deviceFault"})
        public Failure(String stepId, String businessCode, HandlingConstraint handlingConstraint,
                       String message, DeviceFault deviceFault) {
            requireText(businessCode, "businessCode");
            if (handlingConstraint == null) {
                throw new IllegalArgumentException("handlingConstraint 不能为空。");
            }
            requireText(message, "message");
            this.stepId = normalize(stepId);
            this.businessCode = businessCode.trim();
            this.handlingConstraint = handlingConstraint;
            this.message = message.trim();
            this.deviceFault = deviceFault;
        }

        private Failure withHandlingConstraint(HandlingConstraint constraint) {
            return new Failure(stepId, businessCode, constraint, message, deviceFault);
        }
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class DeviceFault {
        String vendor;
        String deviceType;
        String code;
        String message;

        @ConstructorProperties({"vendor", "deviceType", "code", "message"})
        public DeviceFault(String vendor, String deviceType, String code, String message) {
            this.vendor = normalize(vendor);
            this.deviceType = normalize(deviceType);
            this.code = normalize(code);
            this.message = normalize(message);
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

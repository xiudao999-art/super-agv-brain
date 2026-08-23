package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/**
 * Action 模块向执行引擎交付的最终结果。
 *
 * <p>一个 actionInstanceId 只交付一次结果：成功或失败。子动作开始、重试、跳过等
 * 过程事实只在 Action 模块内持久化并输出日志，不扩大执行引擎的接口。</p>
 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionReport {
    /** 状态机流程实例标识；设备联调场景允许为空。 */
    String workflowInstanceId;
    /** 状态机节点实例标识；设备联调场景允许为空。 */
    String workflowNodeInstanceId;
    /** 本次 Action 物理执行的幂等标识。 */
    String actionInstanceId;
    String actionKey;
    String robotId;
    /** true 表示主 Action 已确认成功；false 表示进入失败处理。 */
    boolean success;
    /** false 表示无法证明物理动作成功还是失败，执行引擎不得自动重发。 */
    boolean physicalResultKnown;
    /** 比布尔值更精确的物理结果；状态机遇到 UNKNOWN 时不得自动重发。 */
    PhysicalOutcome physicalOutcome;
    Instant completedAt;
    /** 主 Action 成功时的下游输出；失败时为空。 */
    JsonNode output;
    /** 主 Action 失败时的处置信息；成功时为空。 */
    Failure failure;

    @ConstructorProperties({"workflowInstanceId", "workflowNodeInstanceId", "actionInstanceId",
            "actionKey", "robotId", "success", "physicalResultKnown", "physicalOutcome",
            "completedAt", "output", "failure"})
    public ActionExecutionReport(String workflowInstanceId,
                                 String workflowNodeInstanceId,
                                 String actionInstanceId,
                                 String actionKey,
                                 String robotId,
                                 boolean success,
                                 boolean physicalResultKnown,
                                 PhysicalOutcome physicalOutcome,
                                 Instant completedAt,
                                 JsonNode output,
                                 Failure failure) {
        if (success && failure != null) {
            throw new IllegalArgumentException("成功的 Action 执行报告不能包含 failure。");
        }
        if (!success && failure == null) {
            throw new IllegalArgumentException("失败的 Action 执行报告必须包含 failure。");
        }
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.actionInstanceId = actionInstanceId;
        this.actionKey = actionKey;
        this.robotId = robotId;
        this.success = success;
        this.physicalResultKnown = success || physicalResultKnown;
        this.physicalOutcome = normalizePhysicalOutcome(
                success, this.physicalResultKnown, physicalOutcome);
        this.completedAt = completedAt;
        this.output = output == null ? null : output.deepCopy();
        this.failure = success ? null : normalizeFailure(failure, this.physicalOutcome);
    }

    private static PhysicalOutcome normalizePhysicalOutcome(boolean success,
                                                             boolean physicalResultKnown,
                                                             PhysicalOutcome physicalOutcome) {
        if (success) return PhysicalOutcome.CONFIRMED_SUCCEEDED;
        if (!physicalResultKnown) return PhysicalOutcome.UNKNOWN;
        if (physicalOutcome == null) return PhysicalOutcome.CONFIRMED_FAILED;
        if (physicalOutcome == PhysicalOutcome.CONFIRMED_SUCCEEDED) {
            throw new IllegalArgumentException("失败的 Action 执行报告不能标记为 CONFIRMED_SUCCEEDED。");
        }
        return physicalOutcome;
    }

    private static Failure normalizeFailure(Failure failure, PhysicalOutcome physicalOutcome) {
        if (failure.businessDisposition() == BusinessDisposition.RETRYABLE
                && (physicalOutcome == PhysicalOutcome.UNKNOWN
                || physicalOutcome == PhysicalOutcome.PARTIALLY_COMPLETED)) {
            return failure.withBusinessDisposition(BusinessDisposition.MANUAL_INTERVENTION);
        }
        return failure;
    }

    /** 执行引擎只需要理解的四种失败处置分类。 */
    public enum FailureHandling {
        RETRYABLE,
        MANUAL_INTERVENTION,
        NON_RETRYABLE,
        CRITICAL
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Failure {
        String phaseId;
        String subAction;
        String businessCode;
        String reasonCode;
        BusinessDisposition businessDisposition;
        /** 兼容现有 Java 调用方；JSON 契约统一使用 businessDisposition。 */
        @JsonIgnore
        FailureHandling handling;
        String matchedRuleId;
        String mappingProfileId;
        String message;
        String handlingAdvice;
        RobotClientFault robotClientFault;
        @JsonProperty("deviceFault")
        DeviceError deviceError;

        public Failure(String phaseId,
                       String subAction,
                       String businessCode,
                       String reasonCode,
                       BusinessDisposition businessDisposition,
                       String matchedRuleId,
                       String mappingProfileId,
                       String message,
                       String handlingAdvice,
                       RobotClientFault robotClientFault,
                       DeviceError deviceError) {
            this.phaseId = phaseId;
            this.subAction = subAction;
            this.businessCode = businessCode;
            this.reasonCode = reasonCode;
            this.businessDisposition = businessDisposition;
            this.handling = FailureHandling.valueOf(businessDisposition.name());
            this.matchedRuleId = matchedRuleId;
            this.mappingProfileId = mappingProfileId;
            this.message = message;
            this.handlingAdvice = handlingAdvice;
            this.robotClientFault = robotClientFault;
            this.deviceError = deviceError;
        }

        private Failure withBusinessDisposition(BusinessDisposition disposition) {
            return new Failure(phaseId, subAction, businessCode, reasonCode, disposition,
                    matchedRuleId, mappingProfileId, message, handlingAdvice,
                    robotClientFault, deviceError);
        }
    }

    /** 下游执行器自身的统一技术异常，不与厂家原始码混用。 */
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class RobotClientFault {
        String code;
        String message;
        String category;
        String severity;
        String recoveryStrategy;
        boolean retryable;
        JsonNode rawPayload;
    }

    /** 厂商设备的原始异常，只用于展示和诊断，不参与执行引擎流程判断。 */
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class DeviceError {
        String deviceType;
        String vendor;
        String model;
        String deviceId;
        String adapterKey;
        String adapterVersion;
        String code;
        String message;
        JsonNode rawPayload;
    }
}

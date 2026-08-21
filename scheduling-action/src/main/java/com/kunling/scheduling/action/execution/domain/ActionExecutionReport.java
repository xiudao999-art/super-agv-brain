package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Action 模块向执行引擎公开的稳定执行报告。
 *
 * <p>该契约不暴露 TCP 会话、Action 定义快照和联调参数快照。执行引擎仅在
 * {@link Execution#terminal()} 为 true 时驱动流程节点跳转；步骤事件只表达执行事实。</p>
 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionReport {
    String schemaVersion;
    String eventId;
    long sequence;
    EventType eventType;
    Instant occurredAt;
    Correlation correlation;
    Execution execution;
    Step step;
    Result result;
    Failure failure;
    Trace trace;

    public enum EventType {
        ACTION_ACCEPTED,
        ACTION_STARTED,
        STEP_STARTED,
        STEP_SUCCEEDED,
        STEP_RETRYING,
        STEP_SKIPPED,
        STEP_FAILED,
        STEP_PROGRESS,
        ACTION_SUCCEEDED,
        ACTION_FAILED,
        ACTION_HELD,
        ACTION_REJECTED,
        ACTION_CANCELLED
    }

    public enum ExecutionStatus {
        ACCEPTED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        HELD,
        REJECTED,
        CANCELLED
    }

    public enum PhysicalOutcome {
        NOT_STARTED,
        IN_PROGRESS,
        CONFIRMED_SUCCEEDED,
        CONFIRMED_FAILED,
        UNKNOWN
    }

    /** 执行引擎只需要理解的四种业务处置分类。 */
    public enum FailureHandling {
        RETRYABLE,
        MANUAL_INTERVENTION,
        NON_RETRYABLE,
        CRITICAL
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Correlation {
        String workflowInstanceId;
        String workflowNodeInstanceId;
        String actionInstanceId;
        String actionKey;
        String robotId;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Execution {
        ExecutionStatus status;
        boolean terminal;
        PhysicalOutcome physicalOutcome;
        Instant startedAt;
        Instant finishedAt;
        Long durationMs;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Step {
        Integer stepSequence;
        String phaseId;
        String subAction;
        String status;
        Integer attempt;
        Instant startedAt;
        Instant finishedAt;
        Long durationMs;
        StepPolicy policy;
        JsonNode evidence;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class StepPolicy {
        String configured;
        String applied;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Result {
        JsonNode outputs;
        Integer completedStepCount;
        Integer totalStepCount;
        Integer skippedStepCount;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Failure {
        String businessCode;
        FailureHandling handling;
        String message;
        boolean mapped;
        DeviceError deviceError;
        JsonNode diagnostics;
    }

    /** 厂商设备的原始异常，不得覆盖 Action 业务异常码。 */
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class DeviceError {
        String deviceType;
        String vendor;
        String model;
        String deviceId;
        String code;
        String message;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Trace {
        String deviceCommandId;
        String sourceEventId;
        Long sourceSequence;
    }
}

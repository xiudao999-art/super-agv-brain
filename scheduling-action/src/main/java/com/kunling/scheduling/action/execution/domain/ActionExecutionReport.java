package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

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
    Instant completedAt;
    /** 主 Action 成功时的下游输出；失败时为空。 */
    JsonNode output;
    /** 主 Action 失败时的处置信息；成功时为空。 */
    Failure failure;

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
        String businessCode;
        FailureHandling handling;
        String message;
        DeviceError deviceError;
    }

    /** 厂商设备的原始异常，只用于展示和诊断，不参与执行引擎流程判断。 */
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class DeviceError {
        String deviceType;
        String vendor;
        String deviceId;
        String code;
        String message;
    }
}

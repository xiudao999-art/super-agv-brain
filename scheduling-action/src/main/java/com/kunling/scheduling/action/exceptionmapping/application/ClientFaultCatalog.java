package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 无厂家原始异常时使用的固定客户端技术码目录。 */
@Component
public class ClientFaultCatalog {
    private final Map<Integer, Decision> decisions;
    private final Map<String, Integer> codesByReasonCode;

    public ClientFaultCatalog() {
        Map<Integer, Decision> values = new LinkedHashMap<Integer, Decision>();
        register(values, 40201, "ACTION.CLIENT.SESSION_MISMATCH", "CLIENT.SESSION_MISMATCH",
                HandlingConstraint.NON_RETRYABLE, "检查会话和 robotId 后创建新的执行实例");
        register(values, 40202, "ACTION.CLIENT.INVALID_INPUT", "CLIENT.INVALID_ACTION_INPUT",
                HandlingConstraint.NON_RETRYABLE, "修正 Action 参数或能力 Schema 后重新预览");
        register(values, 40203, "ACTION.CLIENT.UNSUPPORTED_OPERATION", "CLIENT.UNSUPPORTED_OPERATION",
                HandlingConstraint.NON_RETRYABLE, "检查下游能力注册与 Action operation");
        register(values, 50201, "ACTION.CLIENT.ROBOT_BUSY", "CLIENT.ROBOT_BUSY",
                HandlingConstraint.RETRYABLE, "等待当前动作结束后由流程策略决定是否重试");
        register(values, 50202, "ACTION.CLIENT.INTERNAL_ERROR", "CLIENT.INTERNAL_EXECUTION_ERROR",
                HandlingConstraint.MANUAL_INTERVENTION, "检查下游执行日志并确认现场状态");
        register(values, 50203, "ACTION.CLIENT.EXECUTION_FAILED", "CLIENT.OPERATION_EXECUTION_FAILED",
                HandlingConstraint.MANUAL_INTERVENTION, "检查步骤事件和设备日志");
        register(values, 50204, "ACTION.CLIENT.RETRY_EXHAUSTED", "CLIENT.RETRY_EXHAUSTED",
                HandlingConstraint.NON_RETRYABLE, "本次包内重试已耗尽");
        register(values, 50101, "ACTION.CLIENT.EXECUTION_HOLD", "CLIENT.EXECUTION_HOLD",
                HandlingConstraint.MANUAL_INTERVENTION, "确认现场并完成人工恢复");
        register(values, 50102, "ACTION.CLIENT.MANUAL_REQUIRED", "CLIENT.MANUAL_REQUIRED",
                HandlingConstraint.MANUAL_INTERVENTION, "按现场作业规范人工处理");
        register(values, 70201, "ACTION.CLIENT.STATE_UNKNOWN", "CLIENT.ACTION_STATE_UNKNOWN",
                HandlingConstraint.MANUAL_INTERVENTION, "查询执行记录并核对现场");
        register(values, 70202, "ACTION.CLIENT.MATERIAL_UNKNOWN", "CLIENT.MATERIAL_STATE_UNKNOWN",
                HandlingConstraint.MANUAL_INTERVENTION, "核对物料和夹爪状态");
        register(values, 70203, "ACTION.CLIENT.PLACEMENT_CONFLICT", "CLIENT.PLACEMENT_STATE_CONFLICT",
                HandlingConstraint.MANUAL_INTERVENTION, "核对放置结果和传感器证据");
        this.decisions = Collections.unmodifiableMap(values);
        Map<String, Integer> reasonCodes = new LinkedHashMap<String, Integer>();
        for (Map.Entry<Integer, Decision> entry : values.entrySet()) {
            Integer previous = reasonCodes.put(normalizeReasonCode(entry.getValue().reasonCode()), entry.getKey());
            if (previous != null) {
                throw new IllegalStateException("客户端技术原因码重复：" + entry.getValue().reasonCode());
            }
        }
        this.codesByReasonCode = Collections.unmodifiableMap(reasonCodes);
    }

    public Decision resolve(Integer clientCode) {
        Decision decision = clientCode == null ? null : decisions.get(clientCode);
        return decision == null
                ? new Decision("ACTION.CLIENT.UNMAPPED_ERROR", "CLIENT.UNMAPPED_ERROR",
                HandlingConstraint.MANUAL_INTERVENTION, "保留下游原始错误并补充客户端技术码目录")
                : decision;
    }

    /** 将 Action 策略使用的统一原因码编译成下游可精确匹配的 clientCode。 */
    public Optional<Integer> findCodeByReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(codesByReasonCode.get(normalizeReasonCode(reasonCode)));
    }

    private void register(Map<Integer, Decision> values, int code, String businessCode,
                          String reasonCode, HandlingConstraint constraint, String advice) {
        values.put(code, new Decision(businessCode, reasonCode, constraint, advice));
    }

    private String normalizeReasonCode(String reasonCode) {
        return reasonCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Value
    @Accessors(fluent = true)
    public static class Decision {
        String businessCode;
        String reasonCode;
        HandlingConstraint handlingConstraint;
        String handlingAdvice;
    }
}

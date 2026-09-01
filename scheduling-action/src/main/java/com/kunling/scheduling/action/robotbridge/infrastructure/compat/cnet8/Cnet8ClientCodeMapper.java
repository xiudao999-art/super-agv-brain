package com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 将 cnet8 字符串技术码归一为 Action 固定的数字 clientCode。 */
@Component
public class Cnet8ClientCodeMapper {
    /** 未登记的下游技术码；ClientFaultCatalog 会将其归入人工处理的未映射异常。 */
    public static final int UNMAPPED_CLIENT_CODE = 59999;

    private final Map<String, Integer> mappings;

    public Cnet8ClientCodeMapper() {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        register(values, 40201, "CLIENT.SESSION_MISMATCH", "CLIENT.SESSION_NOT_REGISTERED");
        register(values, 40202,
                "INVALID_ACTION_INSTANCE_ID", "INVALID_DEVICE_COMMAND_ID", "INVALID_TIMEOUT",
                "EMPTY_PLAN", "PACKAGE_HASH_MISMATCH", "INVALID_STEP_ID", "INVALID_PARAMETERS",
                "MISSING_FAILURE_POLICY", "INVALID_FAILURE_POLICY", "DUPLICATE_FAILURE_RULE",
                "GATE_SKIP_CONFLICT", "INVALID_VERIFICATION", "RETRY_BUDGET_EXCEEDS_TIMEOUT",
                "CLIENT.PREFLIGHT_REJECTED", "CLIENT.DUPLICATE_COMMAND");
        register(values, 40203, "UNSUPPORTED_OPERATION", "CAPABILITY_NOT_REGISTERED");
        register(values, 50201, "CLIENT.ROBOT_BUSY");
        register(values, 50202, "CLIENT.UNHANDLED", "CLIENT.DEVICE_NOT_READY");
        register(values, 50203, "STEP_FAILED");
        register(values, 50204, "RETRY_EXHAUSTED", "CLIENT.RETRY_EXHAUSTED");
        register(values, 50101, "CLIENT.UNKNOWN_HOLD", "CLIENT.EXECUTION_HOLD");
        register(values, 50102, "CLIENT.MANUAL_REQUIRED");
        register(values, 70201,
                "COMMUNICATION_LOST", "ACTION_TIMEOUT", "PHYSICAL_OUTCOME_UNCERTAIN",
                "CLIENT.EVENT_DELIVERY_FAILED", "CLIENT.ACTION_STATE_UNKNOWN");
        register(values, 70202, "CLIENT.MATERIAL_STATE_UNKNOWN");
        register(values, 70203, "CLIENT.PLACEMENT_STATE_CONFLICT");
        this.mappings = Collections.unmodifiableMap(values);
    }

    public Integer map(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) return null;
        Integer mapped = mappings.get(normalize(rawCode));
        return mapped == null ? UNMAPPED_CLIENT_CODE : mapped;
    }

    private void register(Map<String, Integer> values, int code, String... rawCodes) {
        for (String rawCode : rawCodes) {
            String normalized = normalize(rawCode);
            Integer previous = values.put(normalized, code);
            if (previous != null) {
                throw new IllegalStateException("重复的 cnet8 技术码映射：" + rawCode);
            }
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}

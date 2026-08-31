package com.kunling.scheduling.action.exceptionmapping.application;

import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 一次真实厂家异常参与 Action 业务映射的最小输入。 */
@Value
@Accessors(fluent = true)
public class ErrorMappingContext {
    String stepId;
    String operation;
    String vendor;
    String deviceType;
    String rawCode;
    String deviceMessage;

    @ConstructorProperties({"stepId", "operation", "vendor", "deviceType", "rawCode", "deviceMessage"})
    public ErrorMappingContext(String stepId, String operation, String vendor,
                               String deviceType, String rawCode, String deviceMessage) {
        this.stepId = normalize(stepId);
        this.operation = normalize(operation);
        this.vendor = normalize(vendor);
        this.deviceType = normalize(deviceType);
        this.rawCode = preserveNonBlank(rawCode);
        this.deviceMessage = normalize(deviceMessage);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 厂家码保持原样，确保预览和执行都遵循精确匹配。 */
    private static String preserveNonBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}

package com.kunling.scheduling.action.exceptionmapping.application;

import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/**
 * 一次下游异常参与业务映射的稳定输入。
 *
 * <p>phaseId 仅用于在报告中定位失败节点；真正参与匹配的是 subAction、vendor、deviceType 和
 * deviceCode。厂家原始消息单独保留用于诊断，不会被平台业务码覆盖。</p>
 */
@Value
@Accessors(fluent = true)
public class ErrorMappingContext {
    String phaseId;
    String subAction;
    String vendor;
    String deviceType;
    String deviceCode;
    String deviceMessage;
    boolean physicalResultKnown;

    @ConstructorProperties({"phaseId", "subAction", "vendor", "deviceType", "deviceCode",
            "deviceMessage", "physicalResultKnown"})
    public ErrorMappingContext(String phaseId,
                               String subAction,
                               String vendor,
                               String deviceType,
                               String deviceCode,
                               String deviceMessage,
                               boolean physicalResultKnown) {
        this.phaseId = normalize(phaseId);
        this.subAction = normalize(subAction);
        this.vendor = normalize(vendor);
        this.deviceType = normalize(deviceType);
        this.deviceCode = normalize(deviceCode);
        this.deviceMessage = normalize(deviceMessage);
        this.physicalResultKnown = physicalResultKnown;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

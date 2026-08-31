package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 厂家异常映射预览的最小输入。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "厂家异常映射预览请求")
public class ErrorMappingPreviewRequest {
    @Schema(description = "可空原子操作；用于存在真实歧义的厂家码", example = "MOVE_TO_MAP_POINT")
    String operation;
    @Schema(description = "设备厂家标识", required = true, example = "KUNLING")
    String vendor;
    @Schema(description = "设备类型", required = true, example = "CHASSIS")
    String deviceType;
    @Schema(description = "厂家原始码，按原始字符串精确匹配", required = true, example = "10006")
    String rawCode;

    @ConstructorProperties({"operation", "vendor", "deviceType", "rawCode"})
    public ErrorMappingPreviewRequest(String operation, String vendor, String deviceType, String rawCode) {
        this.operation = normalize(operation);
        this.vendor = normalize(vendor);
        this.deviceType = normalize(deviceType);
        this.rawCode = rawCode == null || rawCode.trim().isEmpty() ? null : rawCode;
    }

    public ErrorMappingContext toContext() {
        return new ErrorMappingContext(null, operation, vendor, deviceType, rawCode, null);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 厂家异常的精确核心匹配条件。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "厂家原始异常的精确匹配条件")
public class ErrorMappingRuleMatch {
    @Schema(description = "可空原子操作；为空表示该设备码对所有操作一致")
    String operation;
    @Schema(description = "设备厂家标识")
    String vendor;
    @Schema(description = "设备类型")
    String deviceType;
    @Schema(description = "厂家原始码")
    String rawCode;

    @ConstructorProperties({"operation", "vendor", "deviceType", "rawCode"})
    public ErrorMappingRuleMatch(String operation,
                                 String vendor,
                                 String deviceType,
                                 String rawCode) {
        this.operation = normalizeToNull(operation);
        this.vendor = normalize(vendor);
        this.deviceType = normalize(deviceType);
        this.rawCode = preserveNonBlank(rawCode);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /**
     * 厂家原始码参与精确匹配，除判断空白外不得改写其内容。
     * 例如厂家确实返回带前导空格的码时，它与去空格后的码不是同一个原始事实。
     */
    private static String preserveNonBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}

package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/**
 * 厂家异常的核心匹配条件。
 *
 * <p>规则只描述“哪个子动作、哪个厂家的哪类设备、返回了什么原始码”。
 * phaseId、ActionKey、型号和适配器等诊断信息不参与业务映射，避免规则随模板节点和适配器实现变化。</p>
 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "厂家原始异常的四维核心匹配条件")
public class ErrorMappingRuleMatch {
    @Schema(description = "下游原子子动作", example = "MOVE_TO_MAP_POINT")
    String subAction;
    @Schema(description = "设备厂家标识", example = "HIKROBOT")
    String vendor;
    @Schema(description = "设备类型", example = "CHASSIS")
    String deviceType;
    @Schema(description = "厂家原始码的比较方式", example = "EXACT")
    DeviceCodeMatchType matchType;
    @Schema(description = "厂家原始码、数值范围或通配模式", example = "NAV_TIMEOUT")
    String rawCodePattern;

    @ConstructorProperties({"subAction", "vendor", "deviceType", "matchType", "rawCodePattern"})
    public ErrorMappingRuleMatch(String subAction,
                                 String vendor,
                                 String deviceType,
                                 DeviceCodeMatchType matchType,
                                 String rawCodePattern) {
        this.subAction = defaultWildcard(subAction);
        this.vendor = defaultWildcard(vendor);
        this.deviceType = defaultWildcard(deviceType);
        this.matchType = matchType == null ? DeviceCodeMatchType.EXACT : matchType;
        this.rawCodePattern = defaultWildcard(rawCodePattern);
    }

    private static String defaultWildcard(String value) {
        if (value == null || value.trim().isEmpty()) return "*";
        return value.trim();
    }
}

package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** Action 模板中的业务失败规则；reasonCode 会编译为下游可精确匹配的客户端码或设备事实。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionFailureRule {
    String reasonCode;
    ActionFailureDirective directive;

    @ConstructorProperties({"reasonCode", "directive"})
    public ActionFailureRule(String reasonCode,
                             ActionFailureDirective directive) {
        this.reasonCode = normalize(reasonCode);
        this.directive = directive;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

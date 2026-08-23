package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 可动态维护的一条厂家异常到业务异常的映射规则。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionErrorMappingRule {
    String ruleId;
    String profileId;
    int priority;
    ErrorMappingRuleMatch match;
    ErrorMappingRuleResult result;
    PackageErrorPolicy policy;

    @ConstructorProperties({"ruleId", "profileId", "priority", "match", "result", "policy"})
    public ActionErrorMappingRule(String ruleId,
                                  String profileId,
                                  int priority,
                                  ErrorMappingRuleMatch match,
                                  ErrorMappingRuleResult result,
                                  PackageErrorPolicy policy) {
        this.ruleId = normalize(ruleId);
        this.profileId = normalize(profileId);
        this.priority = priority;
        this.match = match;
        this.result = result;
        this.policy = policy;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

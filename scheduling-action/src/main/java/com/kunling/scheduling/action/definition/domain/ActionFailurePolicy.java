package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.config.ImmutableCollections;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.util.List;

/** 一个 Action 步骤的完整失败策略，始终包含保守默认指令。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionFailurePolicy {
    List<ActionFailureRule> rules;
    ActionFailureDirective defaultDirective;

    @ConstructorProperties({"rules", "defaultDirective"})
    public ActionFailurePolicy(List<ActionFailureRule> rules,
                               ActionFailureDirective defaultDirective) {
        this.rules = rules == null
                ? ImmutableCollections.listOf()
                : ImmutableCollections.copyList(rules);
        this.defaultDirective = defaultDirective;
    }

    public static ActionFailurePolicy stopAndReport() {
        return new ActionFailurePolicy(ImmutableCollections.listOf(),
                ActionFailureDirective.stopAndReport());
    }

}

package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.ImmutableCollections;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Map;

/** 一次组包得到的下游步骤策略。 */
@Value
@Accessors(fluent = true)
public class ActionPolicyCompilation {
    Map<String, ObjectNode> stepPolicies;

    public ActionPolicyCompilation(Map<String, ObjectNode> stepPolicies) {
        this.stepPolicies = ImmutableCollections.copyMap(stepPolicies);
    }

    public ObjectNode policyFor(String stepId) {
        ObjectNode policy = stepPolicies.get(stepId);
        if (policy == null) throw new IllegalArgumentException("找不到 step 的编译策略：" + stepId);
        return policy.deepCopy();
    }
}

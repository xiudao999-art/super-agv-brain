package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionDefinitionValidator;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.exceptionmapping.application.ActionPolicyCompilation;
import com.kunling.scheduling.action.exceptionmapping.application.ActionPolicyProvider;
import org.springframework.stereotype.Component;

/** 将 Action 当前定义编译为可一次性下发的自解释执行包。 */
@Component
public class ActionPackageAssembler {
    public static final String PROTOCOL_VERSION = "2.0";

    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final ActionDefinitionValidator definitionValidator;
    private final ActionPolicyProvider policyProvider;

    public ActionPackageAssembler(ObjectMapper objectMapper,
                                  JsonCodec jsonCodec,
                                  ActionDefinitionValidator definitionValidator,
                                  ActionPolicyProvider policyProvider) {
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.definitionValidator = definitionValidator;
        this.policyProvider = policyProvider;
    }

    public ActionPackagePreview assemble(ActionDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Action 不能为空。");
        definitionValidator.validateExecutable(definition);
        ActionPolicyCompilation policies = policyProvider.compile(definition);

        ArrayNode steps = objectMapper.createArrayNode();
        for (ActionStepDefinition step : definition.steps()) {
            ObjectNode encoded = objectMapper.createObjectNode();
            encoded.put("stepId", step.stepId());
            encoded.put("operation", step.operation());
            encoded.set("params", step.params().deepCopy());
            encoded.put("gate", step.gate());
            encoded.set("onFailure", policies.policyFor(step.stepId()));
            steps.add(encoded);
        }

        ObjectNode executionPlan = objectMapper.createObjectNode();
        executionPlan.set("steps", steps);
        ObjectNode commandInput = objectMapper.createObjectNode();
        commandInput.set("executionPlan", executionPlan);
        String packageHash = jsonCodec.sha256(jsonCodec.writeCanonical(commandInput));
        return new ActionPackagePreview(definition.id(), packageHash,
                definition.timeoutMs(), commandInput);
    }
}

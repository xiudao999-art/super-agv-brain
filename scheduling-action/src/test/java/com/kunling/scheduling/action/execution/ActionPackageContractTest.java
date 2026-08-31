package com.kunling.scheduling.action.execution;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionDefinitionValidator;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailurePolicy;
import com.kunling.scheduling.action.definition.domain.ActionFailureRule;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.exceptionmapping.application.ActionPolicyCompiler;
import com.kunling.scheduling.action.exceptionmapping.application.ClientFaultCatalog;
import com.kunling.scheduling.action.execution.application.ActionPackageAssembler;
import com.kunling.scheduling.action.execution.application.ActionPackagePreview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionPackageContractTest {
    private final JsonCodec codec = new JsonCodec(ActionTestFixtures.MAPPER);
    private final ActionPolicyCompiler compiler = new ActionPolicyCompiler(
            ActionTestFixtures.MAPPER, new ClientFaultCatalog());
    private final ActionPackageAssembler assembler = new ActionPackageAssembler(
            ActionTestFixtures.MAPPER, codec, new ActionDefinitionValidator(),
            definition -> compiler.compile(definition, ImmutableCollections.listOf()));

    @Test
    void generatesPolicyIdFromStepAndOneBasedRuleIndex() {
        ActionDefinition definition = definitionWithClientRule();
        ActionPackagePreview preview = assembler.assemble(definition);

        assertThat(preview.commandInput().at(
                "/executionPlan/steps/0/onFailure/rules/0/policyId").asText())
                .isEqualTo("move.rule.1");
    }

    @Test
    void commandInputContainsOnlyExecutableStepFields() {
        String json = assembler.assemble(definitionWithClientRule()).commandInput().toString();

        assertThat(json).contains("stepId", "operation", "params", "gate", "onFailure")
                .doesNotContain("schemaHash", "displayName", "enabled", "$parameters");
    }

    @Test
    void packageHashIsCalculatedFromCanonicalCommandInput() {
        ActionPackagePreview preview = assembler.assemble(definitionWithClientRule());

        assertThat(preview.packageHash()).isEqualTo(
                codec.sha256(codec.writeCanonical(preview.commandInput())));
    }

    @Test
    void previewExposesOnlyDefinitionIdHashTimeoutAndCommandInput() {
        String json = codec.write(assembler.assemble(definitionWithClientRule()));

        assertThat(json).contains("actionDefinitionId", "packageHash", "timeoutMs", "commandInput")
                .doesNotContain("revision", "snapshot", "parameterSet", "protocolVersion");
    }

    @Test
    void definitionAndCompiledPackageUseDifferentFailurePolicyFieldNames() {
        ActionDefinition definition = definitionWithClientRule();

        String definitionJson = codec.write(definition);
        String packageJson = assembler.assemble(definition).commandInput().toString();

        assertThat(definitionJson).contains("\"directive\"", "\"defaultDirective\"")
                .doesNotContain("\"then\"", "\"default\"");
        assertThat(packageJson).contains("\"then\"", "\"default\"")
                .doesNotContain("\"directive\"", "\"defaultDirective\"");
    }

    private ActionDefinition definitionWithClientRule() {
        ActionFailureRule rule = new ActionFailureRule(
                "CLIENT.ROBOT_BUSY", ActionFailureDirective.stopAndReport());
        ActionFailurePolicy policy = new ActionFailurePolicy(ImmutableCollections.listOf(rule),
                ActionFailureDirective.stopAndReport());
        ActionStepDefinition step = ActionTestFixtures.step(
                "move", "MOVE_TO_MAP_POINT", true, policy);
        return ActionTestFixtures.definition("definition-1", true, step);
    }
}

package com.kunling.scheduling.action.compilation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.compilation.application.ActionCompiler;
import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.definition.application.PublishedAction;
import com.kunling.scheduling.action.definition.application.PublishedActionLookup;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionCompilerTest {

    private ObjectMapper objectMapper;
    private Map<String, CapabilityManifest> capabilities;
    private Map<String, PublishedAction> publishedActions;
    private ActionCompiler compiler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        capabilities = Map.of("test.move", new CapabilityManifest(
                "test.move", "contract-hash-1",
                Map.of("slot", new ParameterSchema(ParameterType.STRING, true, null, List.of(), Map.of(), null)),
                Map.of(), List.of("arm"), CapabilitySideEffect.PHYSICAL,
                CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, false));
        publishedActions = new java.util.HashMap<>();
        CapabilityCatalog catalog = new CapabilityCatalog() {
            @Override
            public List<CapabilityManifest> listAll() {
                return List.copyOf(capabilities.values());
            }

            @Override
            public Optional<CapabilityManifest> find(String capabilityKey) {
                return Optional.ofNullable(capabilities.get(capabilityKey));
            }
        };
        PublishedActionLookup lookup = (key, version) ->
                Optional.ofNullable(publishedActions.get(key + "@" + version));
        compiler = new ActionCompiler(catalog, lookup,
                new ActionProperties(new ActionProperties.Compiler(8, 100, 6, 524_288)),
                new JsonCodec(objectMapper));
    }

    @Test
    void publishedCompositeIsExpandedAndGrouped() throws Exception {
        ActionDefinition composite = read("""
                {"actionKey":"COMBO.MOVE","version":"1.0.0","displayName":"复用移动","entryPoint":false,
                 "inputSchema":{"slot":{"type":"STRING","required":true}},
                 "steps":[{"kind":"CAPABILITY","stepId":"move","displayName":"移动","capabilityKey":"test.move",
                           "with":{"slot":"$input.slot"}}]}
                """);
        var compositeResult = compiler.compile(composite);
        assertThat(compositeResult.success()).isTrue();
        publishedActions.put("COMBO.MOVE@1.0.0",
                new PublishedAction(composite, compositeResult.plan(), compositeResult.planHash()));

        ActionDefinition main = read("""
                {"actionKey":"MAIN.RUN","version":"1.0.0","displayName":"主动作","entryPoint":true,
                 "inputSchema":{"target":{"type":"STRING","required":true}},
                 "steps":[{"kind":"ACTION_REF","stepId":"useCombo","displayName":"引用组合",
                           "actionRef":{"actionKey":"COMBO.MOVE","version":"1.0.0"},
                           "with":{"slot":"$input.target"}}]}
                """);

        var result = compiler.compile(main);

        assertThat(result.success()).isTrue();
        assertThat(result.plan().nodes()).hasSize(1);
        assertThat(result.plan().nodes().getFirst().executionNodeId()).isEqualTo("useCombo/move");
        assertThat(result.plan().nodes().getFirst().capabilityContractHash()).isEqualTo("contract-hash-1");
        assertThat(result.plan().requiredCapabilities().getFirst().contractHash()).isEqualTo("contract-hash-1");
        assertThat(result.plan().nodes().getFirst().groups()).hasSize(1);
        assertThat(result.dependencies()).extracting("actionKey").containsExactly("COMBO.MOVE");
    }

    @Test
    void mainActionCannotBeReferenced() throws Exception {
        ActionDefinition entryPoint = read("""
                {"actionKey":"MAIN.OTHER","version":"1.0.0","displayName":"另一个主动作","entryPoint":true,
                 "steps":[{"kind":"CAPABILITY","stepId":"move","displayName":"移动","capabilityKey":"test.move","with":{"slot":"A"}}]}
                """);
        var targetPlan = compiler.compile(entryPoint);
        publishedActions.put("MAIN.OTHER@1.0.0",
                new PublishedAction(entryPoint, targetPlan.plan(), targetPlan.planHash()));
        ActionDefinition invalid = read("""
                {"actionKey":"MAIN.INVALID","version":"1.0.0","displayName":"非法引用","entryPoint":true,
                 "steps":[{"kind":"ACTION_REF","stepId":"illegal","displayName":"非法",
                           "actionRef":{"actionKey":"MAIN.OTHER","version":"1.0.0"}}]}
                """);

        var result = compiler.compile(invalid);

        assertThat(result.success()).isFalse();
        assertThat(result.issues()).extracting("code")
                .contains("ACTION_REFERENCE_ENTRY_POINT_FORBIDDEN");
    }

    @Test
    void stepOutputExpressionIsRewrittenToTheExpandedCompositeNodeId() throws Exception {
        ActionDefinition composite = read("""
                {"actionKey":"COMBO.CHAIN","version":"1.0.0","displayName":"链式组合","entryPoint":false,
                 "steps":[
                   {"kind":"CAPABILITY","stepId":"first","displayName":"第一步","capabilityKey":"test.move","with":{"slot":"A"}},
                   {"kind":"CAPABILITY","stepId":"second","displayName":"第二步","capabilityKey":"test.move","with":{"slot":"$steps.first.output.slot"}}
                 ]}
                """);
        var compositeResult = compiler.compile(composite);
        publishedActions.put("COMBO.CHAIN@1.0.0",
                new PublishedAction(composite, compositeResult.plan(), compositeResult.planHash()));
        ActionDefinition main = read("""
                {"actionKey":"MAIN.CHAIN","version":"1.0.0","displayName":"链式主动作","entryPoint":true,
                 "steps":[{"kind":"ACTION_REF","stepId":"group","displayName":"引用链式组合",
                           "actionRef":{"actionKey":"COMBO.CHAIN","version":"1.0.0"}}]}
                """);

        var result = compiler.compile(main);

        assertThat(result.success()).isTrue();
        assertThat(result.plan().nodes()).hasSize(2);
        assertThat(result.plan().nodes().get(1).bindings().get("slot").textValue())
                .isEqualTo("$steps.group/first.output.slot");
    }

    @Test
    void compilationPinsOneCapabilityCatalogSnapshot() throws Exception {
        CapabilityManifest snapshot = capabilities.get("test.move");
        CapabilityManifest concurrentlyChanged = new CapabilityManifest("test.move", "changed-during-compile",
                snapshot.inputSchema(), snapshot.outputSchema(), snapshot.resources(), snapshot.sideEffect(),
                snapshot.retrySafety(), snapshot.safetyCritical(), snapshot.requiresMotionSafetyParameters());
        CapabilityCatalog changingCatalog = new CapabilityCatalog() {
            public List<CapabilityManifest> listAll() { return List.of(snapshot); }
            public Optional<CapabilityManifest> find(String capabilityKey) { return Optional.of(concurrentlyChanged); }
        };
        ActionCompiler snapshotCompiler = new ActionCompiler(changingCatalog, (key, version) -> Optional.empty(),
                new ActionProperties(new ActionProperties.Compiler(8, 100, 6, 524_288)),
                new JsonCodec(objectMapper));
        ActionDefinition definition = read("""
                {"actionKey":"MAIN.SNAPSHOT","version":"1.0.0","displayName":"目录快照","entryPoint":true,
                 "steps":[{"kind":"CAPABILITY","stepId":"move","displayName":"移动",
                           "capabilityKey":"test.move","with":{"slot":"A"}}]}
                """);

        var result = snapshotCompiler.compile(definition);

        assertThat(result.success()).isTrue();
        assertThat(result.requiredCapabilities().getFirst().contractHash()).isEqualTo("contract-hash-1");
        assertThat(result.plan().nodes().getFirst().capabilityContractHash()).isEqualTo("contract-hash-1");
    }

    private ActionDefinition read(String json) throws Exception {
        return objectMapper.readValue(json, ActionDefinition.class);
    }
}

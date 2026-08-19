package com.kunling.scheduling.action.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.compilation.application.ActionCompiler;
import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.definition.application.PublishedActionLookup;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.execution.application.ExecutionPlanMaterializer;
import com.kunling.scheduling.action.execution.application.ExecutionValueResolver;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionPlanMaterializerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void boundedLoopIsSortedAndOnlyMatchingBranchesAreMaterialized() throws Exception {
        ActionDefinition definition = objectMapper.readValue("""
                {"actionKey":"MAIN.BATCH","version":"1.0.0","displayName":"批量动作","entryPoint":true,
                 "inputSchema":{"slots":{"type":"ARRAY","required":true,"items":{"type":"OBJECT","required":true,
                    "properties":{"slotId":{"type":"STRING","required":true}}}}},
                 "steps":[{"kind":"FOREACH","stepId":"slots","displayName":"槽位循环","items":"$input.slots",
                    "itemVariable":"$item","maxIterations":3,"orderBy":{"property":"slotId","direction":"ASCENDING"},
                    "steps":[{"kind":"CONDITION","stepId":"choose","displayName":"选择槽位",
                      "condition":{"operator":"EQUAL","left":"$item.slotId","right":"A"},
                      "then":[{"kind":"CAPABILITY","stepId":"a","displayName":"A槽","capabilityKey":"test.move","with":{"slot":"$item.slotId"}}],
                      "else":[{"kind":"CAPABILITY","stepId":"other","displayName":"其他槽","capabilityKey":"test.move","with":{"slot":"$item.slotId"}}]}]}]}
                """, ActionDefinition.class);
        CapabilityManifest manifest = new CapabilityManifest("test.move", "1.0.0",
                Map.of("slot", new ParameterSchema(ParameterType.STRING, true, null, List.of(), Map.of(), null)),
                Map.of(), List.of("arm"), CapabilitySideEffect.PHYSICAL,
                CapabilityRetrySafety.NEVER, false, false);
        CapabilityCatalog catalog = new CapabilityCatalog() {
            public List<CapabilityManifest> listAll() { return List.of(manifest); }
            public Optional<CapabilityManifest> find(String key) { return Optional.of(manifest); }
        };
        ActionCompiler compiler = new ActionCompiler(catalog, (key, version) -> Optional.empty(),
                new ActionProperties(new ActionProperties.Compiler(8, 100, 6, 524_288)),
                new JsonCodec(objectMapper));
        var compileResult = compiler.compile(definition);
        assertThat(compileResult.success()).isTrue();
        assertThat(compileResult.plan().nodes()).hasSize(6);

        var materializer = new ExecutionPlanMaterializer(new ExecutionValueResolver());
        var input = objectMapper.readTree("{\"slots\":[{\"slotId\":\"B\"},{\"slotId\":\"A\"}]}");
        var nodes = materializer.materialize(compileResult.plan(), input, objectMapper.createObjectNode());

        assertThat(nodes).extracting("executionNodeId")
                .containsExactly("slots[0]/choose[T]/a", "slots[1]/choose[F]/other");
        assertThat(nodes).extracting(node -> node.bindings().get("slot").textValue())
                .containsExactly("A", "B");
    }

    @Test
    void actualArrayCannotExceedCompiledLoopBound() throws Exception {
        // 使用与上一用例同样的运行时协议构造最小计划，验证超限时在发出任何原子命令前失败。
        var loop = new com.kunling.scheduling.action.compilation.domain.LoopFrame(
                "items", "$input.items", "$foreach.items[0]", 0, 1, null);
        var node = new com.kunling.scheduling.action.compilation.domain.ExecutionNode(
                "items[0]/run", "run", "执行", "$.steps[0]", "MAIN.TEST", "1.0.0",
                List.of(), List.of(loop), List.of(), "test.move", "1.0.0", Map.of(), 1000,
                null, true, List.of(), true);
        var plan = new com.kunling.scheduling.action.compilation.domain.ExecutionPlan(
                "1.0", "test", "MAIN.TEST", "1.0.0", "hash", List.of(node), List.of(), List.of(), 1, 1);
        var materializer = new ExecutionPlanMaterializer(new ExecutionValueResolver());
        var input = objectMapper.readTree("{\"items\":[1,2]}");

        assertThatThrownBy(() -> materializer.materialize(plan, input, objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过上限");
    }
}

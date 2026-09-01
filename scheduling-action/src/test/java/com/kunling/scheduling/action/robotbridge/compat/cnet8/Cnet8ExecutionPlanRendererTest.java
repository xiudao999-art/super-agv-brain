package com.kunling.scheduling.action.robotbridge.compat.cnet8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ClientCodeMapper;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ExecutionPlanRenderer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Cnet8ExecutionPlanRendererTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final Cnet8ExecutionPlanRenderer renderer =
            new Cnet8ExecutionPlanRenderer(objectMapper, new JsonCodec(objectMapper));

    @Test
    void rendererPreservesOpaqueParametersAndKeepsTransportHashSeparate() throws Exception {
        JsonNode input = input("DEVICE", "STOP_AND_REPORT", 2);
        RobotActionCommand command = command(input);

        JsonNode first = renderer.render(command);
        JsonNode second = renderer.render(command);

        assertThat(first.at("/Steps/0/Parameters/commandId").asText())
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(first.at("/Steps/0/Parameters/nested/speedPercent").asInt()).isEqualTo(20);
        assertThat(first.at("/Steps/0/OnFailure/0/RawCode").asText()).isEqualTo("NAV_TIMEOUT");
        assertThat(first.at("/Steps/0/OnFailure/1/Vendor").asText()).isEqualTo("*");
        // 期望值由下游最新快照的 ExecutionPlanHash.Compute 对同一计划实测生成。
        assertThat(first.path("PackageHash").asText())
                .isEqualTo("051197342fd074769e3a6489d83847701516b0a86fddcd9b1f6d2f310f6009f7")
                .isNotEqualTo("action-hash");
        assertThat(second.path("PackageHash").asText()).isEqualTo(first.path("PackageHash").asText());
        assertThat(input.at("/executionPlan/steps/0").has("parameters")).isFalse();
        assertThat(input.at("/executionPlan/steps/0/params/commandId").asText())
                .isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void clientRulesAreRejectedInsteadOfBeingSilentlyDropped() throws Exception {
        assertThatThrownBy(() -> renderer.render(command(input("CLIENT", "STOP_AND_REPORT", 2))))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("不能等价执行 CLIENT");
    }

    @Test
    void retryExhaustionSkipIsRejectedBecauseCnet8CannotRepresentIt() throws Exception {
        assertThatThrownBy(() -> renderer.render(command(input("DEVICE", "SKIP_STEP", 2))))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("重试耗尽后只能 STOP_AND_REPORT");
    }

    @Test
    void retriesBeyondCnet8LimitAreRejectedBeforeDispatch() throws Exception {
        assertThatThrownBy(() -> renderer.render(command(input("DEVICE", "STOP_AND_REPORT", 4))))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("重试次数必须在 1-3");
    }

    @Test
    void duplicateCaseInsensitiveSelectorsAreRejectedBeforeDispatch() throws Exception {
        JsonNode input = input("DEVICE", "STOP_AND_REPORT", 2);
        ArrayNode rules = (ArrayNode) input.at("/executionPlan/steps/0/onFailure/rules");
        rules.add(rules.get(0).deepCopy());

        assertThatThrownBy(() -> renderer.render(command(input)))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("重复失败规则");
    }

    @Test
    void totalBackoffBudgetIsCheckedWithTheSameCnet8Boundary() throws Exception {
        JsonNode input = input("DEVICE", "STOP_AND_REPORT", 3);
        ObjectNode directive = (ObjectNode) input.at(
                "/executionPlan/steps/0/onFailure/rules/0/then");
        directive.put("delayMs", 20_000);

        assertThatThrownBy(() -> renderer.render(command(input)))
                .isInstanceOf(RobotUnavailableException.class)
                .hasMessageContaining("退避预算");
    }

    @Test
    void symbolicClientCodesUseExplicitMappingAndPreserveUnknownClassification() {
        Cnet8ClientCodeMapper mapper = new Cnet8ClientCodeMapper();
        assertThat(mapper.map("STEP_FAILED")).isEqualTo(50203);
        assertThat(mapper.map("client.unknown_hold")).isEqualTo(50101);
        assertThat(mapper.map("NEW_DOWNSTREAM_CODE"))
                .isEqualTo(Cnet8ClientCodeMapper.UNMAPPED_CLIENT_CODE);
        assertThat(mapper.map("  ")).isNull();
    }

    private RobotActionCommand command(JsonNode input) {
        return new RobotActionCommand("R01", "action-1", "dc-1",
                "2.0", "action-hash", input, 60_000, Instant.EPOCH);
    }

    private JsonNode input(String source, String onExhaust, int maxRetries) throws Exception {
        String when = "CLIENT".equals(source)
                ? "{\"source\":\"CLIENT\",\"code\":50203}"
                : "{\"source\":\"DEVICE\",\"vendor\":\"HIKROBOT\"," +
                "\"deviceType\":\"CHASSIS\",\"code\":\"NAV_TIMEOUT\"}";
        return objectMapper.readTree("{\"executionPlan\":{\"steps\":[{" +
                "\"stepId\":\"move\",\"operation\":\"MOVE_TO_MAP_POINT\"," +
                "\"params\":{\"commandId\":\"0123456789abcdef0123456789abcdef\"," +
                "\"nested\":{\"speedPercent\":20}},\"gate\":false," +
                "\"onFailure\":{\"rules\":[{\"policyId\":\"move.rule.1\"," +
                "\"when\":" + when + ",\"then\":{\"action\":\"RETRY_STEP\"," +
                "\"maxRetries\":" + maxRetries + ",\"delayMs\":1000," +
                "\"onExhaust\":\"" + onExhaust + "\"}}]," +
                "\"default\":{\"action\":\"STOP_AND_REPORT\"}}}]}}");
    }
}

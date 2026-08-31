package com.kunling.scheduling.action.robotbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import com.kunling.scheduling.action.robotbridge.infrastructure.RobotTcpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RobotTcpServerTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private RobotTcpServer server;

    @AfterEach
    void stopServer() { if (server != null) server.stop(); }

    @Test
    void registerDispatchAndEventUseOnlyTheV2Contract() throws Exception {
        LinkedBlockingQueue<RobotActionEvent> events = new LinkedBlockingQueue<RobotActionEvent>();
        server = new RobotTcpServer(new RobotBridgeProperties(true, "127.0.0.1", 0,
                30_000, 10_000, 1_048_576), objectMapper,
                ImmutableCollections.listOf(events::add), ImmutableCollections.listOf());
        server.start();

        try (Socket socket = new Socket("127.0.0.1", server.boundPort())) {
            socket.setSoTimeout(3_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            send(writer, "{\"version\":\"2.0\",\"messageType\":\"REGISTER\",\"messageId\":\"reg-1\"," +
                    "\"clientInstanceId\":\"client-1\",\"robotId\":\"R01\",\"robotType\":\"COMPOSITE\"," +
                    "\"operationCapabilities\":[{\"operation\":\"MOVE_TO_MAP_POINT\"," +
                    "\"minTimeoutMs\":1000,\"maxTimeoutMs\":300000}]," +
                    "\"policyFeatures\":[\"RETRY_STEP\",\"VERIFY_THEN_RETRY\",\"SKIP_STEP\",\"STOP_AND_REPORT\"]}");
            JsonNode ack = objectMapper.readTree(reader.readLine());
            assertThat(ack.path("version").asText()).isEqualTo("2.0");
            assertThat(ack.at("/operationCapabilities/0/operation").asText()).isEqualTo("MOVE_TO_MAP_POINT");

            JsonNode input = objectMapper.readTree("{\"executionPlan\":{\"steps\":[{" +
                    "\"stepId\":\"move\",\"operation\":\"MOVE_TO_MAP_POINT\",\"params\":{},\"gate\":true," +
                    "\"onFailure\":{\"rules\":[{\"policyId\":\"robot-busy\"," +
                    "\"when\":{\"source\":\"CLIENT\",\"code\":50201}," +
                    "\"then\":{\"action\":\"STOP_AND_REPORT\"}}]," +
                    "\"default\":{\"action\":\"STOP_AND_REPORT\"}}}]}}");
            server.dispatch(new RobotActionCommand("R01", "action-1", "dc-1",
                    "2.0", "package-hash", input, 60_000, Instant.EPOCH));
            JsonNode command = objectMapper.readTree(reader.readLine());
            assertThat(command.path("version").asText()).isEqualTo("2.0");
            assertThat(command.at("/input/executionPlan/steps/0/stepId").asText()).isEqualTo("move");
            assertThat(command.at("/input/executionPlan/steps/0/onFailure/rules/0/when/source").asText())
                    .isEqualTo("CLIENT");
            assertThat(command.path("packageHash").asText()).isEqualTo("package-hash");
            assertThat(command.has("configSnapshot")).isFalse();
            assertThat(command.has("workflowInstanceId")).isFalse();

            send(writer, "{\"version\":\"2.0\",\"messageType\":\"ACTION_EVENT\",\"messageId\":\"event-1\"," +
                    "\"sessionId\":\"" + ack.path("sessionId").asText() + "\",\"robotId\":\"R01\"," +
                    "\"actionInstanceId\":\"action-1\",\"deviceCommandId\":\"dc-1\",\"sequence\":1," +
                    "\"state\":\"ACCEPTED\",\"stepEvent\":{\"eventType\":\"STEP_STARTED\"," +
                    "\"stepSequence\":1,\"stepId\":\"move\",\"operation\":\"MOVE_TO_MAP_POINT\",\"attempt\":1}," +
                    "\"timestamp\":\"2026-08-25T02:17:30.1234567+00:00\"}");
            RobotActionEvent event = events.poll(3, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            assertThat(event.stepEvent().path("eventType").asText()).isEqualTo("STEP_STARTED");
            assertThat(event.stepEvent().path("stepId").asText()).isEqualTo("move");

            send(writer, "{\"version\":\"2.0\",\"messageType\":\"ACTION_STATUS\",\"messageId\":\"status-1\"," +
                    "\"sessionId\":\"" + ack.path("sessionId").asText() + "\",\"robotId\":\"R01\"," +
                    "\"actionInstanceId\":\"action-1\",\"deviceCommandId\":\"dc-1\",\"sequence\":1," +
                    "\"state\":\"RUNNING\",\"physicalOutcome\":\"UNKNOWN\"," +
                    "\"timestamp\":\"2026-08-25T02:17:31Z\"}");
            assertThat(events.poll(300, TimeUnit.MILLISECONDS))
                    .as("ACTION_STATUS 不属于 2.0 线协议，不能进入 ACTION_EVENT 路径")
                    .isNull();
            assertThat(reader.readLine())
                    .as("收到已删除的协议消息后必须关闭当前非法会话")
                    .isNull();
        }
    }

    @Test
    void wireStatesAreExactV2States() {
        assertThat(RobotActionEvent.State.fromWireState("FINISHED")).isEqualTo(RobotActionEvent.State.FINISHED);
        assertThatThrownBy(() -> RobotActionEvent.State.fromWireState("Busy"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RobotActionEvent.State.fromWireState("finished"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RobotActionEvent.State.fromWireState("CANCELLED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void send(BufferedWriter writer, String json) throws Exception {
        writer.write(json); writer.newLine(); writer.flush();
    }
}

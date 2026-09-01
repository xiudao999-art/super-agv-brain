package com.kunling.scheduling.action.robotbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ActionEventNormalizer;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ClientCodeMapper;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ExecutionPlanRenderer;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ProtocolAdapter;
import com.kunling.scheduling.action.robotbridge.infrastructure.RobotTcpServer;
import com.kunling.scheduling.action.robotbridge.infrastructure.protocol.RobotWireDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
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
        server = newServer(events);
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
    void cnet8DialectCompletesTwoStageRegistrationAndNormalizesCommandAndEvent() throws Exception {
        LinkedBlockingQueue<RobotActionEvent> events = new LinkedBlockingQueue<RobotActionEvent>();
        server = newServer(events);
        server.start();

        try (Socket socket = new Socket("127.0.0.1", server.boundPort())) {
            socket.setSoTimeout(3_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));

            send(writer, "{\"MessageId\":\"reg-cnet-1\",\"MessageName\":\"REGISTER\"," +
                    "\"MessageType\":\"REGISTER\",\"RobotId\":\"R01\"," +
                    "\"ActionInstanceId\":\"\",\"DeviceCommandId\":\"\"," +
                    "\"Timestamp\":\"2026-09-01T01:00:00+00:00\"," +
                    "\"MessageInfo\":{\"RobotId\":\"R01\"}}");
            JsonNode ack = objectMapper.readTree(reader.readLine());
            assertThat(ack.path("MessageType").asText()).isEqualTo("REGISTER_ACK");
            assertThat(ack.at("/MessageInfo/SessionId").asText()).isNotBlank();
            assertThat(server.findSession("R01")).as("未上报能力时不能成为可下发会话").isEmpty();

            send(writer, "{\"MessageId\":\"device-reg-1\",\"MessageName\":\"RegisterRobot\"," +
                    "\"MessageType\":\"RegisterRobot\",\"RobotId\":\"R01\"," +
                    "\"Timestamp\":\"2026-09-01T01:00:01+00:00\",\"MessageInfo\":{" +
                    "\"RobotId\":\"R01\",\"Vendor\":\"KUNLING\",\"ProtocolVersion\":\"2.0\"," +
                    "\"Capabilities\":[{\"Operation\":\"MOVE_TO_MAP_POINT\"," +
                    "\"DeviceType\":\"CHASSIS\",\"SchemaVersion\":\"2.0\"}]}}");
            assertThat(awaitSession("R01").get().operationCapabilities())
                    .containsKey("MOVE_TO_MAP_POINT");

            String sessionId = ack.at("/MessageInfo/SessionId").asText();
            send(writer, "{\"MessageId\":\"ping-1\",\"MessageName\":\"PING\"," +
                    "\"MessageType\":\"PING\",\"RobotId\":\"R01\"," +
                    "\"Timestamp\":\"2026-09-01T01:00:02+00:00\",\"MessageInfo\":{" +
                    "\"SessionId\":\"" + sessionId + "\",\"Sequence\":1}}");
            JsonNode pong = objectMapper.readTree(reader.readLine());
            assertThat(pong.path("MessageType").asText()).isEqualTo("PONG");
            assertThat(pong.at("/MessageInfo/SessionId").asText()).isEqualTo(sessionId);

            JsonNode input = objectMapper.readTree("{\"executionPlan\":{\"steps\":[{" +
                    "\"stepId\":\"move\",\"operation\":\"MOVE_TO_MAP_POINT\"," +
                    "\"params\":{\"commandId\":\"0123456789abcdef0123456789abcdef\"," +
                    "\"chassisCommandModelType\":1,\"chassisMoveRequestParams\":{" +
                    "\"chassisMoveRequestType\":1,\"speedPercent\":20," +
                    "\"mapName\":\"LAB\",\"mapPointName\":\"P01\"}}," +
                    "\"gate\":true,\"onFailure\":{\"rules\":[{" +
                    "\"policyId\":\"move.rule.1\",\"when\":{\"source\":\"DEVICE\"," +
                    "\"vendor\":\"HIKROBOT\",\"deviceType\":\"CHASSIS\",\"code\":\"NAV_TIMEOUT\"}," +
                    "\"then\":{\"action\":\"RETRY_STEP\",\"maxRetries\":2," +
                    "\"delayMs\":1000,\"onExhaust\":\"STOP_AND_REPORT\"}}]," +
                    "\"default\":{\"action\":\"STOP_AND_REPORT\"}}}]}}");
            server.dispatch(new RobotActionCommand("R01", "action-1", "dc-1",
                    "2.0", "action-package-hash", input, 60_000, Instant.EPOCH));
            JsonNode command = objectMapper.readTree(reader.readLine());
            assertThat(command.path("MessageType").asText()).isEqualTo("COMMAND");
            assertThat(command.path("ActionInstanceId").asText()).isEqualTo("action-1");
            assertThat(command.at("/MessageInfo/Steps/0/Parameters/commandId").asText())
                    .isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(command.at("/MessageInfo/Steps/0/OnFailure")).hasSize(2);
            assertThat(command.at("/MessageInfo/PackageHash").asText())
                    .isEqualTo("2df62770ea37202b6e12719bc24c5a0cc62f08037844335ba72af136044846e7")
                    .isNotEqualTo("action-package-hash");

            String downstreamHash = command.at("/MessageInfo/PackageHash").asText();
            String originalMessageId = command.path("MessageId").asText();
            send(writer, "{\"MessageId\":\"event-1\",\"MessageName\":\"ACTION_EVENT\"," +
                    "\"MessageType\":\"ACTION_EVENT\",\"RobotId\":\"R01\"," +
                    "\"ActionInstanceId\":\"action-1\",\"DeviceCommandId\":\"dc-1\"," +
                    "\"Timestamp\":\"2026-09-01T01:00:03+00:00\",\"MessageInfo\":{" +
                    "\"OriginalMessageId\":\"" + originalMessageId + "\"," +
                    "\"ActionInstanceId\":\"action-1\",\"DeviceCommandId\":\"dc-1\"," +
                    "\"WorkflowInstanceId\":\"\",\"WorkflowNodeInstanceId\":\"\"," +
                    "\"PackageHash\":\"" + downstreamHash + "\",\"EventKind\":\"FINAL\"," +
                    "\"State\":\"FAILED\",\"PhysicalOutcome\":\"CONFIRMED_FAILED\"," +
                    "\"ClientCode\":\"STEP_FAILED\",\"Message\":\"前方障碍持续存在\"," +
                    "\"DeviceFault\":{\"Vendor\":\"HIKROBOT\",\"DeviceType\":\"CHASSIS\"," +
                    "\"RawCode\":\"NAV_TIMEOUT\",\"RawMessage\":\"前方障碍持续存在\"," +
                    "\"Operation\":\"MOVE_TO_MAP_POINT\"}," +
                    "\"LastStepEvent\":{\"StepId\":\"move\",\"Operation\":\"MOVE_TO_MAP_POINT\"," +
                    "\"Success\":false,\"Skipped\":false,\"Attempts\":3," +
                    "\"PhysicalOutcome\":\"CONFIRMED_FAILED\",\"Message\":\"前方障碍持续存在\"," +
                    "\"CompletedAt\":\"2026-09-01T01:00:03+00:00\"}," +
                    "\"ResolvedSteps\":[{\"StepId\":\"move\",\"Operation\":\"MOVE_TO_MAP_POINT\"," +
                    "\"Success\":false,\"Skipped\":false,\"Attempts\":3," +
                    "\"PhysicalOutcome\":\"CONFIRMED_FAILED\",\"Message\":\"前方障碍持续存在\"}]," +
                    "\"OccurredAt\":\"2026-09-01T01:00:03+00:00\"}}");

            RobotActionEvent event = events.poll(3, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            assertThat(event.sequence()).isEqualTo(1L);
            assertThat(event.state()).isEqualTo(RobotActionEvent.State.FAILED);
            assertThat(event.stepEvent().path("stepId").asText()).isEqualTo("move");
            assertThat(event.error().path("clientCode").asInt()).isEqualTo(50203);
            assertThat(event.error().path("rawClientCode").asText()).isEqualTo("STEP_FAILED");
            assertThat(event.error().at("/deviceFault/code").asText()).isEqualTo("NAV_TIMEOUT");
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

    @Test
    void registrationDialectIsChosenOnceAndMixedShapesAreRejected() throws Exception {
        assertThat(RobotWireDialect.detectRegistration(
                objectMapper.readTree("{\"version\":\"2.0\",\"messageType\":\"REGISTER\"}")))
                .isEqualTo(RobotWireDialect.ACTION_V2);
        assertThat(RobotWireDialect.detectRegistration(
                objectMapper.readTree("{\"MessageType\":\"REGISTER\",\"MessageInfo\":{}}")))
                .isEqualTo(RobotWireDialect.CNET8_V2);
        assertThatThrownBy(() -> RobotWireDialect.detectRegistration(objectMapper.readTree(
                "{\"version\":\"2.0\",\"messageType\":\"REGISTER\"," +
                        "\"MessageType\":\"REGISTER\",\"MessageInfo\":{}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能符合一种");
    }

    private RobotTcpServer newServer(LinkedBlockingQueue<RobotActionEvent> events) {
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        Cnet8ExecutionPlanRenderer renderer = new Cnet8ExecutionPlanRenderer(objectMapper, jsonCodec);
        Cnet8ClientCodeMapper clientCodeMapper = new Cnet8ClientCodeMapper();
        Cnet8ProtocolAdapter adapter = new Cnet8ProtocolAdapter(objectMapper, renderer,
                new Cnet8ActionEventNormalizer(objectMapper, clientCodeMapper));
        return new RobotTcpServer(new RobotBridgeProperties(true, "127.0.0.1", 0,
                30_000, 10_000, 1_048_576), objectMapper, adapter,
                ImmutableCollections.listOf(events::add), ImmutableCollections.listOf());
    }

    private Optional<com.kunling.scheduling.action.robotbridge.application.RobotSessionView>
    awaitSession(String robotId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        Optional<com.kunling.scheduling.action.robotbridge.application.RobotSessionView> session;
        do {
            session = server.findSession(robotId);
            if (session.isPresent()) return session;
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        return Optional.empty();
    }

    private void send(BufferedWriter writer, String json) throws Exception {
        writer.write(json); writer.newLine(); writer.flush();
    }
}

package com.kunling.scheduling.action.robotbridge;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RobotTcpServerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private RobotTcpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void registerHeartbeatDispatchAndEventUseOneJsonLineProtocol() throws Exception {
        LinkedBlockingQueue<RobotActionEvent> receivedEvents = new LinkedBlockingQueue<RobotActionEvent>();
        RobotActionEventListener listener = receivedEvents::add;
        RobotBridgeProperties properties = new RobotBridgeProperties(true, "127.0.0.1", 0,
                30_000, 10_000, 1_048_576,
                ImmutableCollections.listOf("MOVE", "ARM.HOME", "ARM.PICK", "ARM.PLACE",
                        "VISION.CAPTURE"));
        server = new RobotTcpServer(properties, objectMapper, ImmutableCollections.listOf(listener), ImmutableCollections.listOf());
        server.start();

        try (Socket socket = new Socket("127.0.0.1", server.boundPort())) {
            socket.setSoTimeout(3_000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            send(writer, "{\"version\":\"1.0\",\"messageType\":\"REGISTER\",\"messageId\":\"reg-1\",\n \"clientInstanceId\":\"client-1\",\"robotId\":\"ROBOT-01\",\"robotType\":\"COMPOSITE\",\n \"clientVersion\":\"1.0\",\"protocolVersion\":\"1.0\",\"devices\":[],\n \"executionModes\":[\"PACKAGE\"],\n \"capabilities\":[\n   {\"actionType\":\"MOVE\",\"actionVersion\":\"1.0\",\"schemaHash\":\"m1\",\"executionMode\":\"PACKAGE\"},\n   {\"actionType\":\"VISION.CAPTURE\",\"actionVersion\":\"1.0\",\"schemaHash\":\"v1\",\"executionMode\":\"PACKAGE\"}],\n \"snapshot\":{\"state\":\"IDLE\",\"emergency\":false,\"chassisConnected\":true,\n   \"armConnected\":true,\"timestamp\":\"2026-08-19T00:00:00Z\"},\n \"timestamp\":\"2026-08-19T00:00:00Z\"}\n");
            com.fasterxml.jackson.databind.JsonNode ack = objectMapper.readTree(reader.readLine());
            assertThat(ack.path("messageType").textValue()).isEqualTo("REGISTER_ACK");
            assertThat(ack.path("replyTo").textValue()).isEqualTo("reg-1");
            assertThat(ack.at("/acceptedCapabilities/0/actionType").textValue()).isEqualTo("MOVE");
            assertThat(ack.at("/acceptedCapabilities/1/actionType").textValue()).isEqualTo("VISION.CAPTURE");
            assertThat(ack.path("rejectedCapabilities")).isEmpty();

            send(writer, String.format("{\"version\":\"1.0\",\"messageType\":\"PING\",\"messageId\":\"ping-1\",\n \"sessionId\":\"%s\",\"robotId\":\"ROBOT-01\",\"sequence\":1,\n \"snapshot\":{\"state\":\"IDLE\",\"emergency\":false,\"chassisConnected\":true,\n   \"armConnected\":true,\"timestamp\":\"2026-08-19T00:00:01Z\"},\n \"timestamp\":\"2026-08-19T00:00:01Z\"}\n", ack.path("sessionId").textValue()));
            com.fasterxml.jackson.databind.JsonNode pong = objectMapper.readTree(reader.readLine());
            assertThat(pong.path("messageType").textValue()).isEqualTo("PONG");
            assertThat(pong.path("replyTo").textValue()).isEqualTo("ping-1");

            server.dispatch(new RobotActionCommand("ROBOT-01", "action-1", "device-1",
                    "workflow-1", "node-1", "1.0", "template-hash",
                    objectMapper.readTree("{\"MainAction\":{\"actionType\":\"MOVE\",\"phases\":[]}}"),
                    35_000, Instant.parse("2026-08-19T00:00:02Z"),
                    "WAREHOUSE.MOVE", 3L, "parameter-set-1", 2L));
            com.fasterxml.jackson.databind.JsonNode command = objectMapper.readTree(reader.readLine());
            assertThat(command.path("messageType").textValue()).isEqualTo("COMMAND");
            assertThat(command.path("executionMode").textValue()).isEqualTo("PACKAGE");
            assertThat(command.at("/input/MainAction/actionType").textValue()).isEqualTo("MOVE");
            assertThat(command.path("actionVersion").textValue()).isEqualTo("1.0");
            assertThat(command.at("/configSnapshot/actionKey").textValue()).isEqualTo("WAREHOUSE.MOVE");

            send(writer, String.format("{\"version\":\"1.0\",\"messageType\":\"ACTION_EVENT\",\"messageId\":\"event-1\",\n \"sessionId\":\"%s\",\"robotId\":\"ROBOT-01\",\"actionInstanceId\":\"action-1\",\n \"deviceCommandId\":\"device-1\",\"sequence\":1,\"state\":\"ACCEPTED\",\n \"timestamp\":\"2026-08-19T07:46:55.1269881+00:00\"}\n", ack.path("sessionId").textValue()));

            RobotActionEvent receivedEvent = receivedEvents.poll(3, TimeUnit.SECONDS);
            assertThat(receivedEvent).isNotNull();
            assertThat(receivedEvent.state()).isEqualTo(RobotActionEvent.State.ACCEPTED);

            // 解析客户端时间戳后，TCP 会话不能被服务端误判为异常并关闭。
            send(writer, String.format("{\"version\":\"1.0\",\"messageType\":\"PING\",\"messageId\":\"ping-2\",\n \"sessionId\":\"%s\",\"robotId\":\"ROBOT-01\",\"sequence\":2,\n \"snapshot\":{\"state\":\"IDLE\",\"emergency\":false,\"chassisConnected\":true,\n   \"armConnected\":true,\"timestamp\":\"2026-08-19T07:46:56.1269881+00:00\"},\n \"timestamp\":\"2026-08-19T07:46:56.1269881+00:00\"}\n", ack.path("sessionId").textValue()));
            com.fasterxml.jackson.databind.JsonNode pongAfterEvent = objectMapper.readTree(reader.readLine());
            assertThat(pongAfterEvent.path("messageType").textValue()).isEqualTo("PONG");
            assertThat(pongAfterEvent.path("replyTo").textValue()).isEqualTo("ping-2");
        }
    }

    @Test
    void mapsCnet8TerminalAndHoldStatesToUpstreamEvidenceStates() {
        assertThat(RobotActionEvent.State.fromWireState("Finished"))
                .isEqualTo(RobotActionEvent.State.PHYSICAL_DONE);
        assertThat(RobotActionEvent.State.fromWireState("Error"))
                .isEqualTo(RobotActionEvent.State.FAILED);
        assertThat(RobotActionEvent.State.fromWireState("Hang"))
                .isEqualTo(RobotActionEvent.State.UNKNOWN);
        assertThat(RobotActionEvent.State.fromWireState("Busy"))
                .isEqualTo(RobotActionEvent.State.FAILED);
    }

    private void send(BufferedWriter writer, String json) throws Exception {
        writer.write(json.replace("\r", "").replace("\n", ""));
        writer.newLine();
        writer.flush();
    }
}

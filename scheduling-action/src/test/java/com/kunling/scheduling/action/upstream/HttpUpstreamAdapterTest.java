package com.kunling.scheduling.action.upstream;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.config.UpstreamProperties;
import com.kunling.scheduling.action.upstream.application.AtomicActionOutcome;
import com.kunling.scheduling.action.upstream.application.AtomicActionRequest;
import com.kunling.scheduling.action.upstream.infrastructure.HttpUpstreamAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUpstreamAdapterTest {

    private HttpServer server;
    private HttpUpstreamAdapter adapter;
    private final AtomicReference<String> idempotencyHeader = new AtomicReference<>();
    private final AtomicReference<com.fasterxml.jackson.databind.JsonNode> submittedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/atomic-capabilities", exchange -> json(exchange, 200, """
                {"capabilities":[{
                  "capabilityKey":"test.move",
                  "inputSchema":{},"outputSchema":{},"resources":["arm"],
                  "sideEffect":"PHYSICAL","retrySafety":"NEVER",
                  "safetyCritical":false,"requiresMotionSafetyParameters":false
                }]}
                """));
        server.createContext("/api/v1/robots/robot-1/atomic-actions", exchange -> {
            idempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            submittedBody.set(new JsonMapper().readTree(exchange.getRequestBody()));
            respondJson(exchange, 202,
                    "{\"consumeId\":\"consume-1\",\"state\":\"ACCEPTED\",\"physicalResultKnown\":false}");
        });
        server.createContext("/api/v1/atomic-actions/consume-1", exchange ->
                json(exchange, 200, """
                        {"consumeId":"consume-1","state":"SUCCEEDED","physicalResultKnown":true,
                         "output":{"confirmed":true},"evidence":{"deviceTaskId":"task-1"}}
                        """));
        server.start();
        var properties = new UpstreamProperties(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMinutes(5));
        adapter = new HttpUpstreamAdapter(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsVersionlessCatalogAndUsesConsumeIdForSubmissionAndStatusPolling() throws Exception {
        assertThat(adapter.fetchCapabilities()).extracting("capabilityKey").containsExactly("test.move");

        var result = adapter.execute(new AtomicActionRequest("robot-1", "consume-1",
                "workflow-1", "workflow-node-1", "test.move",
                new JsonMapper().createObjectNode(), 1000));

        assertThat(result.outcome()).isEqualTo(AtomicActionOutcome.SUCCEEDED);
        assertThat(idempotencyHeader.get()).isNull();
        assertThat(submittedBody.get().path("consumeId").textValue()).isEqualTo("consume-1");
        assertThat(submittedBody.get().has("capabilityVersion")).isFalse();
        assertThat(result.output().get("confirmed").booleanValue()).isTrue();
        assertThat(result.evidence().get("deviceTaskId").textValue()).isEqualTo("task-1");
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        // 读取请求体后再响应，避免 JDK HttpServer 在复用连接时提前关闭输入流。
        exchange.getRequestBody().readAllBytes();
        respondJson(exchange, status, body);
    }

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

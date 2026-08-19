package com.kunling.scheduling.action.upstream.infrastructure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kunling.scheduling.action.config.UpstreamProperties;
import com.kunling.scheduling.action.execution.domain.ExecutionError;
import com.kunling.scheduling.action.upstream.application.AtomicActionGateway;
import com.kunling.scheduling.action.upstream.application.AtomicActionOutcome;
import com.kunling.scheduling.action.upstream.application.AtomicActionRequest;
import com.kunling.scheduling.action.upstream.application.AtomicActionResult;
import com.kunling.scheduling.action.upstream.application.AtomicCapabilityDescriptor;
import com.kunling.scheduling.action.upstream.application.UpstreamCapabilitySource;
import com.kunling.scheduling.action.upstream.application.UpstreamUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对不可修改上游的 HTTP 防腐层。若上游现有协议不同，只替换本类即可，领域与编排代码无需改变。
 */
@Component
@ConditionalOnProperty(prefix = "kunling.action.upstream", name = "enabled", havingValue = "true")
public class HttpUpstreamAdapter implements AtomicActionGateway, UpstreamCapabilitySource {

    private final RestTemplate client;
    private final UpstreamProperties properties;

    public HttpUpstreamAdapter(RestTemplateBuilder builder, UpstreamProperties properties) {
        if (properties.baseUrl() == null || properties.baseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("启用上游 HTTP Adapter 时必须配置 UPSTREAM_BASE_URL。");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toTimeoutMillis(properties.connectTimeout()));
        requestFactory.setReadTimeout(toTimeoutMillis(properties.requestTimeout()));
        this.client = builder.rootUri(properties.baseUrl())
                .requestFactory(() -> requestFactory)
                .build();
        this.properties = properties;
    }

    @Override
    public List<AtomicCapabilityDescriptor> fetchCapabilities() {
        try {
            CapabilityCatalogResponse response = client.getForObject(
                    "/api/v1/atomic-capabilities", CapabilityCatalogResponse.class
            );
            if (response == null || response.capabilities() == null) {
                throw new UpstreamUnavailableException("上游返回了空的原子能力目录响应。");
            }
            return Collections.unmodifiableList(
                    new ArrayList<AtomicCapabilityDescriptor>(response.capabilities())
            );
        } catch (RestClientException exception) {
            throw unavailable("读取上游原子能力目录失败。", exception);
        }
    }

    @Override
    public AtomicActionResult execute(AtomicActionRequest request) throws InterruptedException {
        CommandStatus status;
        try {
            status = client.postForObject(
                    "/api/v1/robots/{robotId}/atomic-actions",
                    new CreateCommandRequest(request.consumeId(), request.workflowInstanceId(),
                            request.nodeInstanceId(), request.capabilityKey(),
                            request.input(), request.timeoutMs()),
                    CommandStatus.class,
                    request.robotId()
            );
        } catch (RestClientException exception) {
            // POST 连接异常无法判断上游是否已经受理，调用方必须进入 HOLD。
            throw unavailable("提交原子 Action 后无法确认上游是否已受理。", exception);
        }
        if (status == null || status.consumeId() == null || status.consumeId().trim().isEmpty()) {
            throw new UpstreamUnavailableException("上游原子 Action 响应缺少 consumeId。");
        }
        if (!request.consumeId().equals(status.consumeId())) {
            throw new UpstreamUnavailableException("上游返回的 consumeId 与提交请求不一致。");
        }
        if (status.state() == null) {
            throw new UpstreamUnavailableException("上游原子 Action 响应缺少 state。");
        }

        long deadline = System.nanoTime() + Duration.ofMillis(request.timeoutMs()).toNanos();
        while (!status.state().isTerminal()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return unknown("ATOMIC_ACTION_TIMEOUT", "等待上游原子 Action 终态超时。", status.evidence());
            }
            long sleepNanos = Math.min(properties.pollInterval().toNanos(), remaining);
            TimeUnit.NANOSECONDS.sleep(sleepNanos);
            status = queryStatus(request.consumeId());
        }
        return toResult(status);
    }

    private CommandStatus queryStatus(String consumeId) {
        try {
            CommandStatus status = client.getForObject(
                    "/api/v1/atomic-actions/{consumeId}", CommandStatus.class, consumeId
            );
            if (status == null) {
                throw new UpstreamUnavailableException("上游返回了空的原子 Action 状态。");
            }
            if (status.state() == null) {
                throw new UpstreamUnavailableException("上游原子 Action 状态缺少 state。");
            }
            if (!consumeId.equals(status.consumeId())) {
                throw new UpstreamUnavailableException("上游状态响应的 consumeId 与查询请求不一致。");
            }
            return status;
        } catch (RestClientException exception) {
            throw unavailable("查询原子 Action 状态失败，物理结果未知。", exception);
        }
    }

    private AtomicActionResult toResult(CommandStatus status) {
        boolean physicalKnown = Boolean.TRUE.equals(status.physicalResultKnown());
        if (status.state() == CommandState.SUCCEEDED && physicalKnown) {
            return new AtomicActionResult(AtomicActionOutcome.SUCCEEDED,
                    status.output(), status.evidence(), null);
        }
        if (status.state() == CommandState.UNKNOWN || !physicalKnown) {
            return unknown(status.error() == null ? "ATOMIC_ACTION_UNKNOWN" : status.error().code(),
                    status.error() == null ? "上游无法确定原子 Action 的物理结果。" : status.error().message(),
                    status.evidence());
        }
        ExecutionError error = status.error() == null
                ? new ExecutionError(status.state() == CommandState.CANCELLED
                        ? "ATOMIC_ACTION_CANCELLED" : "ATOMIC_ACTION_FAILED",
                        status.state() == CommandState.CANCELLED ? "上游取消了原子 Action。" : "上游原子 Action 执行失败。",
                        true, false, null, "交由工作流决定后续处置。")
                : new ExecutionError(status.error().code(), status.error().message(), true,
                        status.error().retryable(), status.error().deviceCode(), status.error().handlingAdvice());
        return new AtomicActionResult(AtomicActionOutcome.FAILED, status.output(), status.evidence(), error);
    }

    private AtomicActionResult unknown(String code, String message, JsonNode evidence) {
        return new AtomicActionResult(AtomicActionOutcome.UNKNOWN, null, evidence,
                new ExecutionError(code, message, false, false, null, "检查机器人现场状态后人工处置。"));
    }

    private UpstreamUnavailableException unavailable(String message, Exception cause) {
        return new UpstreamUnavailableException(message + " " + cause.getMessage(), cause);
    }

    private int toTimeoutMillis(Duration timeout) {
        long millis = timeout.toMillis();
        return (int) Math.min(Math.max(millis, 1L), Integer.MAX_VALUE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class CapabilityCatalogResponse {
        List<AtomicCapabilityDescriptor> capabilities;
        @ConstructorProperties({"capabilities"})
        public CapabilityCatalogResponse(
                List<AtomicCapabilityDescriptor> capabilities
        ) {
            this.capabilities = capabilities;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class CreateCommandRequest {
        String consumeId;
        String workflowInstanceId;
        String workflowNodeInstanceId;
        String capabilityKey;
        JsonNode input;
        int timeoutMs;
        @ConstructorProperties({"consumeId", "workflowInstanceId", "workflowNodeInstanceId", "capabilityKey", "input", "timeoutMs"})
        public CreateCommandRequest(
                String consumeId,
                String workflowInstanceId,
                String workflowNodeInstanceId,
                String capabilityKey,
                JsonNode input,
                int timeoutMs
        ) {
            this.consumeId = consumeId;
            this.workflowInstanceId = workflowInstanceId;
            this.workflowNodeInstanceId = workflowNodeInstanceId;
            this.capabilityKey = capabilityKey;
            this.input = input;
            this.timeoutMs = timeoutMs;
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class CommandStatus {
        String consumeId;
        CommandState state;
        Boolean physicalResultKnown;
        JsonNode output;
        JsonNode evidence;
        ExecutionError error;
        @ConstructorProperties({"consumeId", "state", "physicalResultKnown", "output", "evidence", "error"})
        public CommandStatus(
                String consumeId,
                CommandState state,
                Boolean physicalResultKnown,
                JsonNode output,
                JsonNode evidence,
                ExecutionError error
        ) {
            this.consumeId = consumeId;
            this.state = state;
            this.physicalResultKnown = physicalResultKnown;
            this.output = output;
            this.evidence = evidence;
            this.error = error;
        }

    }

    private enum CommandState {
        ACCEPTED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        UNKNOWN,
        CANCELLED;

        boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == UNKNOWN || this == CANCELLED;
        }
    }
}

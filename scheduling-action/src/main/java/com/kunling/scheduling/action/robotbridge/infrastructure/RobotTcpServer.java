package com.kunling.scheduling.action.robotbridge.infrastructure;

import com.kunling.scheduling.action.config.ImmutableCollections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ProtocolAdapter;
import com.kunling.scheduling.action.robotbridge.infrastructure.protocol.RobotWireDialect;
import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.config.NamedDaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 机器人 TCP 服务端。
 *
 * <p>本类只负责一行一 JSON 的协议、会话与消息路由；动作状态机和数据库事务留在 Action 模块。</p>
 */
@Component
public class RobotTcpServer implements SmartLifecycle, RobotActionTransport {

    private static final Logger log = LoggerFactory.getLogger(RobotTcpServer.class);
    private static final String PROTOCOL_VERSION = "2.0";

    private final RobotBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final Cnet8ProtocolAdapter cnet8ProtocolAdapter;
    private final List<RobotActionEventListener> actionEventListeners;
    private final List<RobotSessionListener> sessionListeners;
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executor;

    public RobotTcpServer(RobotBridgeProperties properties,
                          ObjectMapper objectMapper,
                          Cnet8ProtocolAdapter cnet8ProtocolAdapter,
                          List<RobotActionEventListener> actionEventListeners,
                          List<RobotSessionListener> sessionListeners) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cnet8ProtocolAdapter = cnet8ProtocolAdapter;
        this.actionEventListeners = ImmutableCollections.copyList(actionEventListeners);
        this.sessionListeners = ImmutableCollections.copyList(sessionListeners);
    }

    @Override
    public synchronized void start() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(properties.bindAddress(), properties.port()));
            serverSocket = socket;
            executor = Executors.newFixedThreadPool(
                    ActionModuleDefaults.ROBOT_BRIDGE_WORKER_THREADS,
                    new NamedDaemonThreadFactory("robot-bridge-")
            );
            executor.submit(this::acceptLoop);
            executor.submit(this::leaseWatchdogLoop);
            log.info("机器人 TCP Bridge 已启动，监听 {}:{}", properties.bindAddress(), socket.getLocalPort());
        } catch (IOException exception) {
            running.set(false);
            throw new IllegalStateException("机器人 TCP Bridge 启动失败", exception);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        closeQuietly(serverSocket);
        sessions.values().forEach(ClientSession::close);
        sessions.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("机器人 TCP Bridge 已停止");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    public int boundPort() {
        ServerSocket current = serverSocket;
        return current == null ? -1 : current.getLocalPort();
    }

    @Override
    public DispatchReceipt dispatch(RobotActionCommand command) {
        requireText(command.robotId(), "robotId");
        requireText(command.actionInstanceId(), "actionInstanceId");
        requireText(command.deviceCommandId(), "deviceCommandId");
        if (!PROTOCOL_VERSION.equals(command.protocolVersion())) {
            throw new IllegalArgumentException("COMMAND 线协议版本必须为 2.0");
        }
        if (command.input() == null || !command.input().isObject()) {
            throw new IllegalArgumentException("完整动作包 input 必须是 JSON 对象");
        }
        ClientSession session = requireSession(command.robotId());
        validateExecutionPlan(session, command.input());

        String messageId = newMessageId();
        ObjectNode message;
        if (session.dialect == RobotWireDialect.CNET8_V2) {
            message = cnet8ProtocolAdapter.createCommand(command, messageId);
        } else {
            message = baseMessage("COMMAND", messageId);
            message.put("sessionId", session.sessionId);
            message.put("robotId", command.robotId());
            message.put("actionInstanceId", command.actionInstanceId());
            message.put("deviceCommandId", command.deviceCommandId());
            message.put("packageHash", command.packageHash());
            message.set("input", command.input());
            message.put("timeoutMs", command.timeoutMs());
            message.put("timestamp", command.timestamp().toString());
        }
        session.send(message);
        return new DispatchReceipt(session.sessionId, messageId, command.timestamp());
    }

    @Override
    public Optional<RobotSessionView> findSession(String robotId) {
        return Optional.ofNullable(sessions.get(robotId))
                .filter(ClientSession::dispatchReady).map(ClientSession::view);
    }

    @Override
    public List<RobotSessionView> listSessions() {
        return sessions.values().stream().filter(ClientSession::dispatchReady)
                .map(ClientSession::view).collect(ImmutableCollections.toImmutableList());
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                executor.submit(() -> handleConnection(socket));
            } catch (IOException exception) {
                if (running.get()) {
                    log.error("接受机器人连接失败", exception);
                }
            }
        }
    }

    private void leaseWatchdogLoop() {
        long checkIntervalMs = Math.max(250L,
                Math.min(properties.leaseMs(), properties.heartbeatIntervalMs()) / 2L);
        while (running.get()) {
            try {
                Thread.sleep(checkIntervalMs);
                Instant deadline = Instant.now().minusMillis(properties.leaseMs());
                sessions.values().stream()
                        .filter(session -> session.lastSeenAt.isBefore(deadline))
                        .forEach(session -> {
                            log.warn("机器人心跳租约过期，关闭会话: robotId={}", session.robotId);
                            session.close();
                        });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try (Socket ignored = socket) {
            String line = readLine(socket.getInputStream());
            JsonNode registration = parse(line);
            RobotWireDialect dialect = RobotWireDialect.detectRegistration(registration);
            if (dialect == RobotWireDialect.ACTION_V2) {
                validateProtocolVersion(registration);
                requireMessageType(registration, "REGISTER");
                session = registerActionV2(socket, registration);
            } else {
                session = registerCnet8(socket, registration);
            }
            while (running.get() && !socket.isClosed()) {
                JsonNode message = parse(readLine(socket.getInputStream()));
                if (session.dialect == RobotWireDialect.ACTION_V2) {
                    validateProtocolVersion(message);
                }
                session.lastSeenAt = Instant.now();
                handleMessage(session, message);
            }
        } catch (EOFException exception) {
            log.info("机器人连接已关闭: {}", session == null ? socket.getRemoteSocketAddress() : session.robotId);
        } catch (Exception exception) {
            log.warn("机器人会话异常关闭: {}", exception.getMessage());
        } finally {
            if (session != null) {
                sessions.remove(session.robotId, session);
                session.close();
                if (session.connectedNotified()) {
                    RobotSessionView view = session.view();
                    sessionListeners.forEach(listener -> safeNotifyDisconnected(listener, view));
                }
            }
        }
    }

    private ClientSession registerActionV2(Socket socket, JsonNode registration) {
        String robotId = requiredText(registration, "robotId");
        String clientInstanceId = requiredText(registration, "clientInstanceId");
        String robotType = requiredText(registration, "robotType");
        String replyTo = requiredText(registration, "messageId");
        String sessionId = UUID.randomUUID().toString();

        Map<String, RobotOperationCapability> capabilities = parseOperationCapabilities(registration);
        Set<String> policyFeatures = parsePolicyFeatures(registration);

        ClientSession session = new ClientSession(socket, RobotWireDialect.ACTION_V2,
                sessionId, robotId, robotType, clientInstanceId,
                capabilities, policyFeatures, true, Instant.now());
        ObjectNode ack = baseMessage("REGISTER_ACK", newMessageId());
        ack.put("replyTo", replyTo);
        ack.put("robotId", robotId);
        ack.put("accepted", true);
        ack.put("sessionId", sessionId);
        ack.put("leaseMs", properties.leaseMs());
        ack.put("heartbeatIntervalMs", properties.heartbeatIntervalMs());
        ack.set("operationCapabilities", registration.path("operationCapabilities").deepCopy());
        ack.set("policyFeatures", registration.path("policyFeatures").deepCopy());
        ack.put("serverTime", Instant.now().toString());
        session.send(ack);

        replaceSession(session);
        notifyConnected(session);
        log.info("机器人已注册: robotId={}, sessionId={}, operations={}, policyFeatures={}",
                robotId, sessionId, capabilities.keySet(), policyFeatures);
        return session;
    }

    private ClientSession registerCnet8(Socket socket, JsonNode registration) {
        Cnet8ProtocolAdapter.InitialRegistration initial =
                cnet8ProtocolAdapter.parseInitialRegistration(registration);
        String sessionId = UUID.randomUUID().toString();
        ClientSession session = new ClientSession(socket, RobotWireDialect.CNET8_V2,
                sessionId, initial.robotId(), initial.robotType(), initial.clientInstanceId(),
                java.util.Collections.<String, RobotOperationCapability>emptyMap(),
                java.util.Collections.<String>emptySet(), false, Instant.now());
        replaceSession(session);
        session.send(cnet8ProtocolAdapter.createRegisterAck(initial.robotId(), sessionId,
                properties.heartbeatIntervalMs(), newMessageId()));
        log.info("cnet8 已完成初始注册，等待设备能力: robotId={}, sessionId={}",
                initial.robotId(), sessionId);
        return session;
    }

    private void replaceSession(ClientSession session) {
        ClientSession previous = sessions.put(session.robotId, session);
        if (previous != null) previous.close();
    }

    private void notifyConnected(ClientSession session) {
        if (!session.markConnectedNotified()) return;
        RobotSessionView view = session.view();
        sessionListeners.forEach(listener -> safeNotifyConnected(listener, view));
    }

    private void handleMessage(ClientSession session, JsonNode message) {
        if (session.dialect == RobotWireDialect.CNET8_V2) {
            handleCnet8Message(session, message);
            return;
        }
        validateSession(session, message);
        String messageType = requiredText(message, "messageType");
        switch (messageType) {
            case "PING":
                replyPong(session, message);
                break;
            case "ACTION_EVENT":
                publishActionEvent(message);
                break;
            default:
                throw new IllegalArgumentException("2.0 会话不支持消息类型：" + messageType);
        }
    }

    private void handleCnet8Message(ClientSession session, JsonNode message) {
        cnet8ProtocolAdapter.validateRobotMessage(message, session.robotId);
        String messageType = cnet8ProtocolAdapter.messageType(message);
        switch (messageType) {
            case "PING":
                session.send(cnet8ProtocolAdapter.createPong(message, session.robotId,
                        session.sessionId, newMessageId()));
                break;
            case "RegisterRobot":
                Cnet8ProtocolAdapter.RegistrationCapabilities registration =
                        cnet8ProtocolAdapter.parseRobotRegistration(message, session.robotId);
                session.completeRegistration(registration.operationCapabilities(),
                        registration.policyFeatures());
                notifyConnected(session);
                log.info("cnet8 设备能力已注册: robotId={}, operations={}, policyFeatures={}",
                        session.robotId, session.operationCapabilities.keySet(), session.policyFeatures);
                break;
            case "ACTION_EVENT":
                if (!session.dispatchReady()) {
                    throw new IllegalArgumentException("cnet8 尚未完成 RegisterRobot，不能上报 ACTION_EVENT");
                }
                publishActionEvent(cnet8ProtocolAdapter.parseActionEvent(message,
                        session.robotId, session.sessionId, session.nextActionEventSequence()));
                break;
            default:
                throw new IllegalArgumentException("cnet8 会话不支持消息类型：" + messageType);
        }
    }

    private void replyPong(ClientSession session, JsonNode ping) {
        ObjectNode pong = baseMessage("PONG", newMessageId());
        pong.put("replyTo", requiredText(ping, "messageId"));
        pong.put("sessionId", session.sessionId);
        pong.put("sequence", ping.path("sequence").asLong());
        pong.put("serverTime", Instant.now().toString());
        session.send(pong);
    }

    private void publishActionEvent(JsonNode message) {
        publishActionEvent(parseActionMessage(message));
    }

    private void publishActionEvent(RobotActionEvent event) {
        actionEventListeners.forEach(listener -> safeNotifyEvent(listener, event));
    }

    private RobotActionEvent parseActionMessage(JsonNode message) {
        return new RobotActionEvent(
                requiredText(message, "messageType", 32),
                requiredText(message, "messageId", 64),
                requiredText(message, "sessionId", 64),
                requiredText(message, "robotId", 128),
                requiredText(message, "actionInstanceId", 128),
                requiredText(message, "deviceCommandId", 128),
                requiredPositiveLong(message, "sequence"),
                RobotActionEvent.State.fromWireState(requiredText(message, "state")),
                nullableCopy(message.get("stepEvent")),
                nullableCopy(message.get("resolvedSteps")),
                optionalPhysicalOutcome(message.get("physicalOutcome")),
                nullableCopy(message.get("error")),
                parseProtocolTimestamp(requiredText(message, "timestamp"))
        );
    }

    /**
     * 解析机器人协议时间戳。
     *
     * <p>.NET DateTimeOffset 默认可能输出 7 位小数和 {@code +00:00}，而 Java 8 的
     * {@link Instant#parse(CharSequence)} 不能完整解析部分非 3 位分组的小数格式。
     * ISO_OFFSET_DATE_TIME 可解析 {@code Z}、显式时区及 1～9 位小数。</p>
     */
    private Instant parseProtocolTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("timestamp 必须是带时区的 ISO-8601 时间字符串: " + timestamp,
                    exception);
        }
    }

    private void validateSession(ClientSession session, JsonNode message) {
        if (!session.sessionId.equals(requiredText(message, "sessionId"))
                || !session.robotId.equals(requiredText(message, "robotId"))) {
            throw new IllegalArgumentException("消息会话或 robotId 与当前连接不匹配");
        }
    }

    private ClientSession requireSession(String robotId) {
        ClientSession session = sessions.get(robotId);
        if (session == null || !session.dispatchReady()) {
            throw new RobotUnavailableException("机器人当前未连接: " + robotId);
        }
        return session;
    }

    private ObjectNode baseMessage(String messageType, String messageId) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("version", PROTOCOL_VERSION);
        message.put("messageType", messageType);
        message.put("messageId", messageId);
        return message;
    }

    private void validateProtocolVersion(JsonNode message) {
        if (!PROTOCOL_VERSION.equals(requiredText(message, "version"))) {
            throw new IllegalArgumentException("仅支持线协议 2.0。");
        }
    }

    private Map<String, RobotOperationCapability> parseOperationCapabilities(JsonNode registration) {
        JsonNode source = registration.get("operationCapabilities");
        if (source == null || !source.isArray() || source.size() == 0) {
            throw new IllegalArgumentException("operationCapabilities 必须是非空数组");
        }
        Map<String, RobotOperationCapability> result = new LinkedHashMap<String, RobotOperationCapability>();
        for (JsonNode item : source) {
            String operation = requiredText(item, "operation");
            if (!operation.matches("[A-Z0-9][A-Z0-9._-]{1,127}")) {
                throw new IllegalArgumentException("operation 必须是稳定的大写标识：" + operation);
            }
            int minimum = requiredPositiveInt(item, "minTimeoutMs");
            int maximum = requiredPositiveInt(item, "maxTimeoutMs");
            if (minimum > maximum) {
                throw new IllegalArgumentException(operation + " 的 minTimeoutMs 不能大于 maxTimeoutMs");
            }
            RobotOperationCapability previous = result.put(operation,
                    new RobotOperationCapability(operation, minimum, maximum));
            if (previous != null) throw new IllegalArgumentException("operationCapabilities 重复：" + operation);
        }
        return result;
    }

    private Set<String> parsePolicyFeatures(JsonNode registration) {
        JsonNode source = registration.get("policyFeatures");
        if (source == null || !source.isArray() || source.size() == 0) {
            throw new IllegalArgumentException("policyFeatures 必须是非空数组");
        }
        Set<String> result = new LinkedHashSet<String>();
        for (JsonNode item : source) {
            if (!item.isTextual()) throw new IllegalArgumentException("policyFeatures 元素必须是字符串");
            String feature = item.textValue();
            try {
                ActionFailureDirectiveType.valueOf(feature);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("不支持的失败策略特性：" + feature, exception);
            }
            if (!result.add(feature)) throw new IllegalArgumentException("policyFeatures 重复：" + feature);
        }
        return result;
    }

    private void validateExecutionPlan(ClientSession session, JsonNode input) {
        JsonNode steps = input.at("/executionPlan/steps");
        if (!steps.isArray() || steps.size() == 0) {
            throw new IllegalArgumentException("input.executionPlan.steps 必须是非空数组");
        }
        Set<String> stepIds = new LinkedHashSet<String>();
        for (JsonNode step : steps) {
            String stepId = requiredText(step, "stepId");
            if (!stepIds.add(stepId)) throw new IllegalArgumentException("stepId 重复：" + stepId);
            String operation = requiredText(step, "operation");
            if (!session.operationCapabilities.containsKey(operation)) {
                throw new RobotUnavailableException("机器人当前会话未注册原子操作：" + operation);
            }
            if (!step.path("params").isObject()) throw new IllegalArgumentException(stepId + ".params 必须是对象");
            if (!step.has("gate") || !step.path("gate").isBoolean()) {
                throw new IllegalArgumentException(stepId + ".gate 必须是布尔值");
            }
            JsonNode onFailure = step.path("onFailure");
            if (!onFailure.isObject() || !onFailure.path("default").isObject()) {
                throw new IllegalArgumentException(stepId + ".onFailure.default 必须存在");
            }
            boolean gate = step.path("gate").booleanValue();
            if (gate && validateWireDirective(session, onFailure.path("default"), stepId)) {
                throw new IllegalArgumentException("门禁步骤 " + stepId + " 的 default 不能最终跳过");
            }
            JsonNode rules = onFailure.path("rules");
            if (!rules.isArray()) throw new IllegalArgumentException(stepId + ".onFailure.rules 必须是数组");
            for (JsonNode rule : rules) {
                requiredText(rule, "policyId");
                JsonNode when = rule.path("when");
                if (!when.isObject()) {
                    throw new IllegalArgumentException(stepId + ".onFailure.rules.when 必须是对象");
                }
                String source = requiredText(when, "source");
                if ("CLIENT".equals(source)) {
                    requiredPositiveInt(when, "code");
                    if (when.has("vendor") || when.has("deviceType")) {
                        throw new IllegalArgumentException(stepId + " 的 CLIENT 规则不能携带设备匹配字段");
                    }
                } else if ("DEVICE".equals(source)) {
                    requiredText(when, "vendor");
                    requiredText(when, "deviceType");
                    requiredText(when, "code");
                } else {
                    throw new IllegalArgumentException(stepId
                            + ".onFailure.rules.when.source 只能是 CLIENT 或 DEVICE");
                }
                if (gate && validateWireDirective(session, rule.path("then"), stepId)) {
                    throw new IllegalArgumentException("门禁步骤 " + stepId + " 的规则不能最终跳过");
                }
            }
        }
    }

    private boolean validateWireDirective(ClientSession session, JsonNode directive, String stepId) {
        String action = requiredText(directive, "action");
        if (!session.policyFeatures.contains(action)) {
            throw new RobotUnavailableException("机器人当前会话不支持失败策略：" + action);
        }
        String onExhaust = directive.path("onExhaust").asText(null);
        if (onExhaust != null && !session.policyFeatures.contains(onExhaust)) {
            throw new RobotUnavailableException("机器人当前会话不支持耗尽策略：" + onExhaust);
        }
        JsonNode verify = directive.get("verify");
        boolean verifyThenRetry = "VERIFY_THEN_RETRY".equals(action);
        if (verifyThenRetry && (verify == null || !verify.isObject())) {
            throw new IllegalArgumentException(stepId + " 的 VERIFY_THEN_RETRY 必须配置 verify");
        }
        if (!verifyThenRetry && verify != null && !verify.isNull()) {
            throw new IllegalArgumentException(stepId + " 只有 VERIFY_THEN_RETRY 可以配置 verify");
        }
        if (verifyThenRetry) {
            String operation = requiredText(verify, "operation");
            if (!verify.path("params").isObject()) {
                throw new IllegalArgumentException(stepId + ".verify.params 必须是对象");
            }
            if (!session.operationCapabilities.containsKey(operation)) {
                throw new RobotUnavailableException(stepId + " 的复核操作未注册：" + operation);
            }
        }
        boolean retry = "RETRY_STEP".equals(action) || "VERIFY_THEN_RETRY".equals(action);
        if (retry) {
            int retries = requiredPositiveInt(directive, "maxRetries");
            if (retries > 10) throw new IllegalArgumentException(stepId + ".maxRetries 不能超过 10");
            if (onExhaust == null) throw new IllegalArgumentException(stepId + " 的重试策略必须配置 onExhaust");
            if (!"STOP_AND_REPORT".equals(onExhaust) && !"SKIP_STEP".equals(onExhaust)) {
                throw new IllegalArgumentException(stepId + ".onExhaust 只能是 STOP_AND_REPORT 或 SKIP_STEP");
            }
        } else if (onExhaust != null) {
            throw new IllegalArgumentException(stepId + " 的非重试策略不能配置 onExhaust");
        } else if (directive.has("maxRetries") || directive.has("delayMs")) {
            throw new IllegalArgumentException(stepId + " 的非重试策略不能配置重试参数");
        }
        int delayMs = directive.path("delayMs").asInt(0);
        if (delayMs < 0 || delayMs > 3_600_000) {
            throw new IllegalArgumentException(stepId + ".delayMs 必须在 0-3600000 之间");
        }
        return "SKIP_STEP".equals(action) || "SKIP_STEP".equals(onExhaust);
    }

    private int requiredPositiveInt(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new IllegalArgumentException(field + " 必须是正整数");
        }
        return value.intValue();
    }

    private long requiredPositiveLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0L) {
            throw new IllegalArgumentException(field + " 必须是正整数");
        }
        return value.longValue();
    }

    private PhysicalOutcome optionalPhysicalOutcome(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("physicalOutcome 必须是字符串");
        try {
            return PhysicalOutcome.valueOf(value.textValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 physicalOutcome：" + value.textValue(), exception);
        }
    }

    private String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value == -1) {
                if (buffer.size() == 0) {
                    throw new EOFException("连接已关闭");
                }
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                if (buffer.size() >= properties.maximumMessageBytes()) {
                    throw new IOException("单条机器人消息超过限制: " + properties.maximumMessageBytes());
                }
                buffer.write(value);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private JsonNode parse(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("机器人消息不是合法 JSON", exception);
        }
    }

    private void requireMessageType(JsonNode message, String expected) {
        String actual = requiredText(message, "messageType");
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("新连接首条消息必须是 " + expected + "，实际为 " + actual);
        }
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private String requiredText(JsonNode parent, String field, int maximumLength) {
        String value = requiredText(parent, field);
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maximumLength);
        }
        return value;
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    private JsonNode nullableCopy(JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }

    private void putNullable(ObjectNode target, String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(field, value);
        }
    }

    private void safeNotifyEvent(RobotActionEventListener listener, RobotActionEvent event) {
        try {
            listener.onEvent(event);
        } catch (RuntimeException exception) {
            log.error("处理机器人动作事件失败: actionInstanceId={}", event.actionInstanceId(), exception);
        }
    }

    private void safeNotifyConnected(RobotSessionListener listener, RobotSessionView view) {
        try {
            listener.onConnected(view);
        } catch (RuntimeException exception) {
            log.error("处理机器人上线事件失败: robotId={}", view.robotId(), exception);
        }
    }

    private void safeNotifyDisconnected(RobotSessionListener listener, RobotSessionView view) {
        try {
            listener.onDisconnected(view);
        } catch (RuntimeException exception) {
            log.error("处理机器人离线事件失败: robotId={}", view.robotId(), exception);
        }
    }

    private static String newMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭阶段不覆盖原始退出原因。
            }
        }
    }

    private final class ClientSession {
        private final Socket socket;
        private final OutputStream output;
        private final RobotWireDialect dialect;
        private final String sessionId;
        private final String robotId;
        private final String robotType;
        private final String clientInstanceId;
        private volatile Map<String, RobotOperationCapability> operationCapabilities;
        private volatile Set<String> policyFeatures;
        private final Instant connectedAt;
        private final AtomicBoolean connectedNotification = new AtomicBoolean();
        private final AtomicLong actionEventSequence = new AtomicLong();
        private volatile boolean ready;
        private volatile Instant lastSeenAt;

        private ClientSession(Socket socket,
                              RobotWireDialect dialect,
                              String sessionId,
                              String robotId,
                              String robotType,
                              String clientInstanceId,
                              Map<String, RobotOperationCapability> operationCapabilities,
                              Set<String> policyFeatures,
                              boolean ready,
                              Instant connectedAt) {
            try {
                this.output = socket.getOutputStream();
            } catch (IOException exception) {
                throw new RobotUnavailableException("无法创建机器人输出流", exception);
            }
            this.socket = socket;
            this.dialect = dialect;
            this.sessionId = sessionId;
            this.robotId = robotId;
            this.robotType = robotType;
            this.clientInstanceId = clientInstanceId;
            this.operationCapabilities = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<String, RobotOperationCapability>(operationCapabilities));
            this.policyFeatures = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<String>(policyFeatures));
            this.ready = ready;
            this.connectedAt = connectedAt;
            this.lastSeenAt = connectedAt;
        }

        private synchronized void send(JsonNode message) {
            try {
                byte[] bytes = (objectMapper.writeValueAsString(message) + "\n").getBytes(StandardCharsets.UTF_8);
                if (bytes.length > properties.maximumMessageBytes()) {
                    throw new RobotUnavailableException("下发消息超过协议大小限制");
                }
                output.write(bytes);
                output.flush();
            } catch (IOException exception) {
                close();
                throw new RobotUnavailableException("机器人连接写入失败: " + robotId, exception);
            }
        }

        private boolean active() {
            return !socket.isClosed() && socket.isConnected();
        }

        private boolean dispatchReady() {
            return ready && active();
        }

        private synchronized void completeRegistration(
                Map<String, RobotOperationCapability> capabilities,
                Set<String> features) {
            if (ready) throw new IllegalArgumentException("cnet8 当前会话已经完成 RegisterRobot");
            this.operationCapabilities = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<String, RobotOperationCapability>(capabilities));
            this.policyFeatures = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<String>(features));
            this.ready = true;
        }

        private boolean markConnectedNotified() {
            return connectedNotification.compareAndSet(false, true);
        }

        private boolean connectedNotified() {
            return connectedNotification.get();
        }

        private long nextActionEventSequence() {
            return actionEventSequence.incrementAndGet();
        }

        private RobotSessionView view() {
            return new RobotSessionView(sessionId, robotId, robotType, clientInstanceId,
                    operationCapabilities, policyFeatures, connectedAt, lastSeenAt);
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 连接已不可用时无需再次传播关闭异常。
            }
        }
    }
}

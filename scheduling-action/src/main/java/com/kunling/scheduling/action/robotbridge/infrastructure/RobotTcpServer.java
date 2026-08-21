package com.kunling.scheduling.action.robotbridge.infrastructure;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.shared.NamedDaemonThreadFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 机器人 TCP 服务端。
 *
 * <p>本类只负责一行一 JSON 的协议、会话与消息路由；动作状态机和数据库事务留在 Action 模块。</p>
 */
@Component
public class RobotTcpServer implements SmartLifecycle, RobotActionTransport {

    private static final Logger log = LoggerFactory.getLogger(RobotTcpServer.class);
    private static final String PROTOCOL_VERSION = "1.0";

    private final RobotBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final List<RobotActionEventListener> actionEventListeners;
    private final List<RobotSessionListener> sessionListeners;
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executor;

    public RobotTcpServer(RobotBridgeProperties properties, ObjectMapper objectMapper,
                          List<RobotActionEventListener> actionEventListeners,
                          List<RobotSessionListener> sessionListeners) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        if (command.input() == null || !command.input().isObject()) {
            throw new IllegalArgumentException("完整动作包 input 必须是 JSON 对象");
        }
        String actionType = command.input().at("/MainAction/actionType").asText();
        if (actionType.trim().isEmpty()) {
            throw new IllegalArgumentException("input.MainAction.actionType 不能为空");
        }

        ClientSession session = requireSession(command.robotId());
        if (!session.acceptedActionTypes.contains(actionType)) {
            throw new RobotUnavailableException("机器人 " + command.robotId()
                    + " 当前会话未声明可执行 " + actionType);
        }

        String messageId = newMessageId();
        ObjectNode message = baseMessage("COMMAND", messageId);
        message.put("sessionId", session.sessionId);
        message.put("robotId", command.robotId());
        message.put("actionInstanceId", command.actionInstanceId());
        message.put("deviceCommandId", command.deviceCommandId());
        putNullable(message, "workflowInstanceId", command.workflowInstanceId());
        putNullable(message, "nodeInstanceId", command.nodeInstanceId());
        // actionVersion 是 cnet8 既有线协议字段，Java 领域中明确命名为协议兼容号。
        message.put("actionVersion", command.protocolActionVersion());
        message.put("executionMode", "PACKAGE");
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("actionKey", command.actionKey());
        snapshot.put("actionRevision", command.actionRevision());
        putNullable(snapshot, "parameterSetId", command.parameterSetId());
        if (command.parameterSetRevision() == null) {
            snapshot.putNull("parameterSetRevision");
        } else {
            snapshot.put("parameterSetRevision", command.parameterSetRevision());
        }
        snapshot.put("packageHash", command.packageHash());
        message.set("configSnapshot", snapshot);
        message.set("input", command.input());
        message.put("timeoutMs", command.timeoutMs());
        message.put("timestamp", command.timestamp().toString());
        session.send(message);
        return new DispatchReceipt(session.sessionId, messageId, command.timestamp());
    }

    @Override
    public void query(RobotActionQuery query) {
        ClientSession session = requireSession(query.robotId());
        ObjectNode message = baseMessage("QUERY_ACTION", newMessageId());
        message.put("sessionId", session.sessionId);
        message.put("robotId", query.robotId());
        message.put("actionInstanceId", query.actionInstanceId());
        message.put("deviceCommandId", query.deviceCommandId());
        session.send(message);
    }

    @Override
    public Optional<RobotSessionView> findSession(String robotId) {
        return Optional.ofNullable(sessions.get(robotId)).filter(ClientSession::active).map(ClientSession::view);
    }

    @Override
    public List<RobotSessionView> listSessions() {
        return sessions.values().stream().filter(ClientSession::active).map(ClientSession::view).collect(ImmutableCollections.toImmutableList());
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
            requireMessageType(registration, "REGISTER");
            session = register(socket, registration);
            while (running.get() && !socket.isClosed()) {
                JsonNode message = parse(readLine(socket.getInputStream()));
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
                RobotSessionView view = session.view();
                sessionListeners.forEach(listener -> safeNotifyDisconnected(listener, view));
            }
        }
    }

    private ClientSession register(Socket socket, JsonNode registration) {
        String robotId = requiredText(registration, "robotId");
        String clientInstanceId = requiredText(registration, "clientInstanceId");
        String robotType = requiredText(registration, "robotType");
        String replyTo = requiredText(registration, "messageId");
        String sessionId = UUID.randomUUID().toString();

        Set<String> configuredTypes = new HashSet<>(properties.acceptedActionTypes());
        Set<String> acceptedTypes = new HashSet<>();
        ArrayNode accepted = objectMapper.createArrayNode();
        ArrayNode rejected = objectMapper.createArrayNode();
        for (JsonNode capability : registration.path("capabilities")) {
            String actionType = capability.path("actionType").asText();
            String actionVersion = capability.path("actionVersion").asText();
            boolean supported = configuredTypes.contains(actionType)
                    && "1.0".equals(actionVersion)
                    && "PACKAGE".equals(capability.path("executionMode").asText());
            ObjectNode decision = objectMapper.createObjectNode();
            decision.put("actionType", actionType);
            decision.put("actionVersion", actionVersion);
            if (supported) {
                accepted.add(decision);
                acceptedTypes.add(actionType);
            } else {
                decision.put("reasonCode", "UNSUPPORTED_IN_PHASE_1");
                decision.put("reason", "一期下游未启用该动作或协议版本");
                rejected.add(decision);
            }
        }

        ClientSession session = new ClientSession(socket, sessionId, robotId, robotType,
                clientInstanceId, ImmutableCollections.copySet(acceptedTypes), Instant.now());
        ObjectNode ack = baseMessage("REGISTER_ACK", newMessageId());
        ack.put("replyTo", replyTo);
        ack.put("robotId", robotId);
        ack.put("accepted", true);
        ack.put("sessionId", sessionId);
        ack.put("leaseMs", properties.leaseMs());
        ack.put("heartbeatIntervalMs", properties.heartbeatIntervalMs());
        ack.set("acceptedCapabilities", accepted);
        ack.set("rejectedCapabilities", rejected);
        ack.put("serverTime", Instant.now().toString());
        session.send(ack);

        ClientSession previous = sessions.put(robotId, session);
        if (previous != null) {
            previous.close();
        }
        RobotSessionView view = session.view();
        sessionListeners.forEach(listener -> safeNotifyConnected(listener, view));
        log.info("机器人已注册: robotId={}, sessionId={}, acceptedActions={}",
                robotId, sessionId, acceptedTypes);
        return session;
    }

    private void handleMessage(ClientSession session, JsonNode message) {
        validateSession(session, message);
        String messageType = requiredText(message, "messageType");
        switch (messageType) {
            case "PING":
                replyPong(session, message);
                break;
            case "ACTION_EVENT":
            case "ACTION_STATUS":
                publishEvent(message);
                break;
            default:
                log.debug("忽略机器人未知消息: {}", messageType);
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

    private void publishEvent(JsonNode message) {
        RobotActionEvent event = new RobotActionEvent(
                requiredText(message, "messageType"),
                requiredText(message, "messageId"),
                requiredText(message, "sessionId"),
                requiredText(message, "robotId"),
                requiredText(message, "actionInstanceId"),
                requiredText(message, "deviceCommandId"),
                message.path("sequence").asLong(),
                RobotActionEvent.State.fromWireState(requiredText(message, "state")),
                nullableCopy(message.get("resolvedSteps")),
                nullableCopy(message.get("physicalResult")),
                nullableCopy(message.get("error")),
                nullableCopy(message.get("phaseEvent")),
                nullableCopy(message.get("reportState")),
                parseProtocolTimestamp(requiredText(message, "timestamp"))
        );
        actionEventListeners.forEach(listener -> safeNotifyEvent(listener, event));
    }

    /**
     * 解析机器人协议时间戳。
     *
     * <p>.NET DateTimeOffset 默认可能输出 7 位小数和 {@code +00:00}，而 Java 8 的
     * {@link Instant#parse(CharSequence)} 对部分非 3 位分组的小数格式不兼容。
     * ISO_OFFSET_DATE_TIME 同时兼容 {@code Z}、显式时区及 1～9 位小数。</p>
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
        if (session == null || !session.active()) {
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
        private final String sessionId;
        private final String robotId;
        private final String robotType;
        private final String clientInstanceId;
        private final Set<String> acceptedActionTypes;
        private final Instant connectedAt;
        private volatile Instant lastSeenAt;

        private ClientSession(Socket socket, String sessionId, String robotId, String robotType,
                              String clientInstanceId, Set<String> acceptedActionTypes, Instant connectedAt) {
            try {
                this.output = socket.getOutputStream();
            } catch (IOException exception) {
                throw new RobotUnavailableException("无法创建机器人输出流", exception);
            }
            this.socket = socket;
            this.sessionId = sessionId;
            this.robotId = robotId;
            this.robotType = robotType;
            this.clientInstanceId = clientInstanceId;
            this.acceptedActionTypes = acceptedActionTypes;
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

        private RobotSessionView view() {
            return new RobotSessionView(sessionId, robotId, robotType, clientInstanceId,
                    acceptedActionTypes, connectedAt, lastSeenAt);
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

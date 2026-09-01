package com.kunling.scheduling.action.commissioning.application;

import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 协调不持久化的设备探测。探测事件在此被消费，不会进入业务 Action 状态机。
 *
 * <p>活动注册表和迟到事件隔离表均有明确上限，防止异常客户端持续推送时占用无界内存。</p>
 */
@Component
public class ArmPositionProbeCoordinator {
    static final int DEFAULT_CAPACITY = 64;
    static final Duration LATE_EVENT_RETENTION = Duration.ofSeconds(30);

    private final int capacity;
    private final Duration lateEventRetention;
    private final Clock clock;
    private final Map<String, PendingProbe> activeByActionId = new LinkedHashMap<String, PendingProbe>();
    private final Map<String, String> activeActionIdByRobot = new LinkedHashMap<String, String>();
    private final Map<String, Instant> retiredActionIds = new LinkedHashMap<String, Instant>();

    public ArmPositionProbeCoordinator() {
        this(DEFAULT_CAPACITY, LATE_EVENT_RETENTION, Clock.systemUTC());
    }

    ArmPositionProbeCoordinator(int capacity, Duration lateEventRetention, Clock clock) {
        if (capacity < 1) throw new IllegalArgumentException("探测协调器容量必须大于 0");
        this.capacity = capacity;
        this.lateEventRetention = lateEventRetention;
        this.clock = clock;
    }

    public synchronized ProbeTicket register(String robotId, String actionInstanceId, String deviceCommandId) {
        cleanupRetired();
        requireText(robotId, "robotId");
        requireText(actionInstanceId, "actionInstanceId");
        requireText(deviceCommandId, "deviceCommandId");
        if (activeActionIdByRobot.containsKey(robotId)) {
            throw new ActionConflictException("机器人 " + robotId + " 已有一个位置探测正在进行。");
        }
        if (activeByActionId.containsKey(actionInstanceId) || retiredActionIds.containsKey(actionInstanceId)) {
            throw new ActionConflictException("探测 actionInstanceId 已被使用。");
        }
        if (activeByActionId.size() >= capacity) {
            throw new RobotUnavailableException("位置探测协调器已满，请稍后重试。");
        }
        PendingProbe pending = new PendingProbe(robotId, actionInstanceId, deviceCommandId);
        activeByActionId.put(actionInstanceId, pending);
        activeActionIdByRobot.put(robotId, actionInstanceId);
        return new ProbeTicket(this, pending);
    }

    /** 返回 true 表示该事件属于探测，调用方必须停止后续业务处理。 */
    public synchronized boolean route(RobotActionEvent event) {
        cleanupRetired();
        PendingProbe pending = activeByActionId.get(event.actionInstanceId());
        if (pending == null) return retiredActionIds.containsKey(event.actionInstanceId());
        if (!pending.robotId.equals(event.robotId())) {
            pending.result.completeExceptionally(new RobotUnavailableException("探测事件的机器人标识不一致。"));
            retire(pending);
            return true;
        }
        if (!pending.deviceCommandId.equals(event.deviceCommandId())) {
            pending.result.completeExceptionally(new RobotUnavailableException("探测事件的设备命令标识不一致。"));
            retire(pending);
            return true;
        }
        if (isTerminal(event.state())) {
            pending.result.complete(event);
            retire(pending);
        }
        return true;
    }

    public synchronized void failRobot(String robotId, String message) {
        String actionInstanceId = activeActionIdByRobot.get(robotId);
        PendingProbe pending = actionInstanceId == null ? null : activeByActionId.get(actionInstanceId);
        if (pending == null) return;
        pending.result.completeExceptionally(new RobotUnavailableException(message));
        retire(pending);
    }

    private boolean isTerminal(RobotActionEvent.State state) {
        return state == RobotActionEvent.State.FINISHED || state == RobotActionEvent.State.REJECTED
                || state == RobotActionEvent.State.FAILED || state == RobotActionEvent.State.UNKNOWN;
    }

    private synchronized void cancel(PendingProbe pending) {
        if (activeByActionId.get(pending.actionInstanceId) == pending) retire(pending);
    }

    private void retire(PendingProbe pending) {
        activeByActionId.remove(pending.actionInstanceId);
        activeActionIdByRobot.remove(pending.robotId, pending.actionInstanceId);
        retiredActionIds.put(pending.actionInstanceId, clock.instant().plus(lateEventRetention));
        while (retiredActionIds.size() > capacity * 2) {
            Iterator<String> iterator = retiredActionIds.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private void cleanupRetired() {
        Instant now = clock.instant();
        retiredActionIds.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }

    int activeProbeCount() {
        return activeByActionId.size();
    }

    private static final class PendingProbe {
        private final String robotId;
        private final String actionInstanceId;
        private final String deviceCommandId;
        private final CompletableFuture<RobotActionEvent> result = new CompletableFuture<RobotActionEvent>();

        private PendingProbe(String robotId, String actionInstanceId, String deviceCommandId) {
            this.robotId = robotId;
            this.actionInstanceId = actionInstanceId;
            this.deviceCommandId = deviceCommandId;
        }
    }

    public static final class ProbeTicket implements AutoCloseable {
        private final ArmPositionProbeCoordinator owner;
        private final PendingProbe pending;

        private ProbeTicket(ArmPositionProbeCoordinator owner, PendingProbe pending) {
            this.owner = owner;
            this.pending = pending;
        }

        public RobotActionEvent await(Duration timeout) {
            try {
                return pending.result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                throw new RobotUnavailableException("获取机械臂当前位置超时。", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RobotUnavailableException("获取机械臂当前位置被中断。", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RobotUnavailableException) {
                    throw (RobotUnavailableException) cause;
                }
                throw new RobotUnavailableException("获取机械臂当前位置失败。", cause);
            } finally {
                owner.cancel(pending);
            }
        }

        @Override
        public void close() {
            owner.cancel(pending);
        }
    }
}

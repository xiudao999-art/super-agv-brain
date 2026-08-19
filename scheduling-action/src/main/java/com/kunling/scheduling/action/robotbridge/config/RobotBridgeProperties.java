package com.kunling.scheduling.action.robotbridge.config;

import com.kunling.scheduling.action.config.ActionModuleDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 机器人长连接监听参数；acceptedActionTypes 明确限制一期协议面。 */
public final class RobotBridgeProperties {

    private final boolean enabled;
    private final String bindAddress;
    private final int port;
    private final int leaseMs;
    private final int heartbeatIntervalMs;
    private final int maximumMessageBytes;
    private final List<String> acceptedActionTypes;

    public RobotBridgeProperties(boolean enabled, String bindAddress, int port, int leaseMs,
                                 int heartbeatIntervalMs, int maximumMessageBytes,
                                 List<String> acceptedActionTypes) {
        this.enabled = enabled;
        this.bindAddress = bindAddress == null || bindAddress.trim().isEmpty()
                ? ActionModuleDefaults.ROBOT_BRIDGE_BIND_ADDRESS : bindAddress;
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("robot-bridge.port 必须在 0 到 65535 之间");
        }
        this.port = port;
        this.leaseMs = leaseMs <= 0 ? ActionModuleDefaults.ROBOT_BRIDGE_LEASE_MS : leaseMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs <= 0
                ? ActionModuleDefaults.ROBOT_HEARTBEAT_INTERVAL_MS : heartbeatIntervalMs;
        this.maximumMessageBytes = maximumMessageBytes <= 0
                ? ActionModuleDefaults.ROBOT_MAXIMUM_MESSAGE_BYTES : maximumMessageBytes;
        List<String> actionTypes = acceptedActionTypes == null
                ? ActionModuleDefaults.PHASE_ONE_ACTION_TYPES : acceptedActionTypes;
        this.acceptedActionTypes = Collections.unmodifiableList(new ArrayList<String>(actionTypes));
    }

    public boolean enabled() {
        return enabled;
    }

    public String bindAddress() {
        return bindAddress;
    }

    public int port() {
        return port;
    }

    public int leaseMs() {
        return leaseMs;
    }

    public int heartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public int maximumMessageBytes() {
        return maximumMessageBytes;
    }

    public List<String> acceptedActionTypes() {
        return acceptedActionTypes;
    }
}

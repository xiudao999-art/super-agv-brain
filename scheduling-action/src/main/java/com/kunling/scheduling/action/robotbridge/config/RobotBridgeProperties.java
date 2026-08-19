package com.kunling.scheduling.action.robotbridge.config;

import com.kunling.scheduling.action.config.ActionModuleDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 机器人长连接监听参数。
 *
 * <p>启停、监听网卡和端口属于部署环境差异，允许通过 application.yml 覆盖；
 * 租约、心跳、消息大小及动作白名单属于一期协议安全边界，继续由 Action 模块统一维护。</p>
 */
@ConfigurationProperties(prefix = "kunling.action.robot-bridge")
public class RobotBridgeProperties {

    private boolean enabled;
    private String bindAddress;
    private int port;
    private final int leaseMs;
    private final int heartbeatIntervalMs;
    private final int maximumMessageBytes;
    private final List<String> acceptedActionTypes;

    /** Spring Boot 配置绑定入口，所有未显式配置的字段都使用一期安全默认值。 */
    public RobotBridgeProperties() {
        this(
                ActionModuleDefaults.ROBOT_BRIDGE_ENABLED,
                ActionModuleDefaults.ROBOT_BRIDGE_BIND_ADDRESS,
                ActionModuleDefaults.ROBOT_BRIDGE_PORT,
                ActionModuleDefaults.ROBOT_BRIDGE_LEASE_MS,
                ActionModuleDefaults.ROBOT_HEARTBEAT_INTERVAL_MS,
                ActionModuleDefaults.ROBOT_MAXIMUM_MESSAGE_BYTES,
                ActionModuleDefaults.PHASE_ONE_ACTION_TYPES
        );
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        if (bindAddress == null || bindAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("robot-bridge.bind-address 不能为空");
        }
        this.bindAddress = bindAddress.trim();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("robot-bridge.port 必须在 0 到 65535 之间");
        }
        this.port = port;
    }
}

package com.kunling.scheduling.action.robotbridge.config;

import com.kunling.scheduling.action.config.ActionModuleDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 机器人长连接监听参数。
 *
 * <p>启停、监听网卡和端口属于部署环境差异，允许通过 application.yml 覆盖；
 * 租约、心跳和消息大小属于传输安全边界；原子能力由下游在每次会话中注册。</p>
 */
@ConfigurationProperties(prefix = "kunling.action.robot-bridge")
public class RobotBridgeProperties {

    private boolean enabled;
    private String bindAddress;
    private int port;
    private int leaseMs;
    private int heartbeatIntervalMs;
    private final int maximumMessageBytes;

    /** Spring Boot 配置绑定入口，所有未显式配置的字段都使用一期安全默认值。 */
    public RobotBridgeProperties() {
        this(
                ActionModuleDefaults.ROBOT_BRIDGE_ENABLED,
                ActionModuleDefaults.ROBOT_BRIDGE_BIND_ADDRESS,
                ActionModuleDefaults.ROBOT_BRIDGE_PORT,
                ActionModuleDefaults.ROBOT_BRIDGE_LEASE_MS,
                ActionModuleDefaults.ROBOT_HEARTBEAT_INTERVAL_MS,
                ActionModuleDefaults.ROBOT_MAXIMUM_MESSAGE_BYTES
        );
    }

    public RobotBridgeProperties(boolean enabled, String bindAddress, int port, int leaseMs,
                                 int heartbeatIntervalMs, int maximumMessageBytes) {
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

    public int getLeaseMs() {
        return leaseMs;
    }

    /**
     * 配置服务端判定机器人会话失活前允许的最长静默时间。
     *
     * <p>该值可按部署环境覆盖；正式环境不应依赖增大租约掩盖业务线程阻塞。</p>
     */
    public void setLeaseMs(int leaseMs) {
        if (leaseMs <= 0) {
            throw new IllegalArgumentException("robot-bridge.lease-ms 必须大于 0");
        }
        this.leaseMs = leaseMs;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * 配置通过 REGISTER_ACK 下发给客户端的心跳周期。
     * 客户端连续三个周期未收到 PONG 时会判定会话失效。
     */
    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException("robot-bridge.heartbeat-interval-ms 必须大于 0");
        }
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int maximumMessageBytes() {
        return maximumMessageBytes;
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

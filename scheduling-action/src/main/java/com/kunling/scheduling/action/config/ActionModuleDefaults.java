package com.kunling.scheduling.action.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Action 模块的一期业务默认值。
 *
 * <p>这些值描述一期能力边界，不属于部署环境差异，因此不放入 application.yml。
 * 后续需要扩展动作种类或调整安全限制时，应在模块内部通过策略对象演进。</p>
 */
public final class ActionModuleDefaults {

    public static final long ACTION_TIMEOUT_SCAN_INTERVAL_MS = 1_000L;

    public static final boolean ROBOT_BRIDGE_ENABLED = true;
    public static final String ROBOT_BRIDGE_BIND_ADDRESS = "0.0.0.0";
    public static final int ROBOT_BRIDGE_PORT = 8080;
    public static final int ROBOT_BRIDGE_LEASE_MS = 30_000;
    public static final int ROBOT_HEARTBEAT_INTERVAL_MS = 10_000;
    public static final int ROBOT_MAXIMUM_MESSAGE_BYTES = 1_048_576;
    public static final int ROBOT_BRIDGE_WORKER_THREADS = 64;
    public static final List<String> SUPPORTED_DOWNSTREAM_ACTION_TYPES = Collections.unmodifiableList(
            Arrays.asList("MOVE", "ARM.PICK", "ARM.PLACE", "ARM.PICK_BATCH",
                    "ARM.PLACE_BATCH", "ARM.HOME", "VISION.CAPTURE")
    );

    private ActionModuleDefaults() {
    }
}

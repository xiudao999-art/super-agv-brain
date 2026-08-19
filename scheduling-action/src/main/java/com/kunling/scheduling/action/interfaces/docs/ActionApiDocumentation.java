package com.kunling.scheduling.action.interfaces.docs;

/**
 * Action 模块接口文档的中文分组常量。
 *
 * <p>控制器注解和 OpenAPI 顶层标签共用这些常量，避免分组名称在不同位置发生偏差。</p>
 */
public final class ActionApiDocumentation {

    public static final String TAG_FIXED_ACTION = "一期固定动作执行";
    public static final String TAG_ROBOT_SESSION = "机器人连接管理";
    public static final String TAG_ACTION_MANAGEMENT = "动作配置管理";
    public static final String TAG_CAPABILITY = "原子能力目录";
    public static final String TAG_DYNAMIC_EXECUTION = "动态动作执行";

    private ActionApiDocumentation() {
    }
}

package com.kunling.scheduling.action.robotbridge.infrastructure.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一个 TCP 会话所使用的线协议方言。
 *
 * <p>方言只允许在首条 REGISTER 消息中确定，连接存续期间不得切换，避免宽松字段匹配
 * 把格式错误的消息误判成另一个协议。</p>
 */
public enum RobotWireDialect {
    ACTION_V2,
    CNET8_V2;

    public static RobotWireDialect detectRegistration(JsonNode registration) {
        if (registration == null || !registration.isObject()) {
            throw new IllegalArgumentException("机器人注册消息必须是 JSON 对象");
        }
        boolean actionV2 = registration.has("version") || registration.has("messageType");
        boolean cnet8V2 = registration.has("MessageType") || registration.has("MessageInfo");
        if (actionV2 == cnet8V2) {
            throw new IllegalArgumentException("机器人注册消息必须且只能符合一种已知线协议方言");
        }
        return actionV2 ? ACTION_V2 : CNET8_V2;
    }
}

package com.kunling.scheduling.action.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Action 调试页的线协议参数范例。
 *
 * <p>这些数据只用于用户主动选择“载入范例”，不是 Schema，也不参与服务端组包。</p>
 */
@Component
public class ActionProtocolParameterExamples {
    private static final String ARM_EXAMPLE_COMMAND_ID = "11111111111111111111111111111111";
    private final ObjectMapper objectMapper;

    public ActionProtocolParameterExamples(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, JsonNode> examples() {
        Map<String, JsonNode> examples = new LinkedHashMap<String, JsonNode>();
        examples.put("MOVE_TO_POSE", moveToPose());
        examples.put("GRIP", gripper(1, 30, 40, 20, "55555555555555555555555555555555"));
        putAliases(examples, gripper(1, 100, 40, 20, "55555555555555555555555555555555"),
                "GRIP.OPEN", "GRIP_OPEN");
        putAliases(examples, gripper(1, 0, 40, 20, "55555555555555555555555555555555"),
                "GRIP.CLOSE", "GRIP_CLOSE");
        putAliases(examples, gripper(3, 100, 30, 20, "77777777777777777777777777777777"),
                "GRIP.VERIFY_LOAD", "GRIP_VERIFY_LOAD");
        return examples;
    }

    private ObjectNode moveToPose() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("commandId", ARM_EXAMPLE_COMMAND_ID);
        params.put("armCommandModelType", 1);
        params.putNull("armCommandInfo");
        ObjectNode request = params.putObject("armMoveRequestParams");
        request.put("armMoveRequestType", 1);
        request.put("speedPercent", 10);
        ObjectNode cartesian = request.putObject("armPoseXYZRxRyRz");
        cartesian.put("x", 300.0D);
        cartesian.put("y", 0.0D);
        cartesian.put("z", 400.0D);
        cartesian.put("rx", 180.0D);
        cartesian.put("ry", 0.0D);
        cartesian.put("rz", 0.0D);
        ObjectNode joints = request.putObject("armPoseJ1J2J3J4J5J6");
        putNumbers(joints, new String[]{"j1", "j2", "j3", "j4", "j5", "j6"});
        return params;
    }

    private ObjectNode gripper(int modelType, int targetWidthPercent, int forcePercent,
                               int speedPercent, String commandId) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("commandId", commandId);
        params.put("gripperCommandModelType", modelType);
        params.putNull("gripperCommandInfo");
        ObjectNode request = params.putObject("gripperMoveRequestParams");
        request.put("targetWidthPercent", targetWidthPercent);
        request.put("forcePercent", forcePercent);
        request.put("speedPercent", speedPercent);
        return params;
    }

    private void putAliases(Map<String, JsonNode> examples, JsonNode example, String... operations) {
        for (String operation : operations) examples.put(operation, example.deepCopy());
    }

    private void putNumbers(ObjectNode target, String[] fields) {
        for (String field : fields) target.put(field, 0.0D);
    }
}

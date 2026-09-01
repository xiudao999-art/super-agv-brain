package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** 提供页面编辑提示；实际可执行能力必须以下游当前会话注册结果为准。 */
@Tag(name = "Action 协议目录")
@RestController
@RequestMapping("/api/action-protocol")
public class ActionProtocolCatalogController {
    private final ActionProtocolParameterExamples parameterExamples;

    public ActionProtocolCatalogController(ActionProtocolParameterExamples parameterExamples) {
        this.parameterExamples = parameterExamples;
    }

    @Operation(summary = "查询 Action 2.0 编辑提示")
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("protocolVersion", "2.0");
        result.put("operationSuggestions", ImmutableCollections.listOf(
                "MOVE_TO_MAP_POINT", "MOVE_TO_POSE", "GRIP", "GRIP.OPEN", "GRIP.CLOSE",
                "GRIP.VERIFY_LOAD", "GRIP_OPEN", "GRIP_CLOSE", "GRIP_VERIFY_LOAD",
                "VISION.VERIFY_MATERIAL", "VISION.VERIFY_PLACEMENT",
                "VISION.CAPTURE", "CHASSIS_VERIFY_STOPPED", "ARM_VERIFY_HOME"));
        result.put("failureDirectives", Arrays.asList(ActionFailureDirectiveType.values()));
        result.put("parameterExamples", parameterExamples.examples());
        return result;
    }
}

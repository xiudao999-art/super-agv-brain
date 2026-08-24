package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.definition.domain.DownstreamActionType;
import com.kunling.scheduling.action.definition.domain.DownstreamSubAction;
import com.kunling.scheduling.action.definition.domain.PhaseFailureAction;
import com.kunling.scheduling.action.definition.domain.RetryExhaustedAction;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 页面和调用方使用的协议能力目录，避免在多处复制七种主动作与十种子动作。 */
@Tag(name = "下游动作协议", description = "查询主动作、子动作和异常策略的协议边界")
@RestController
@RequestMapping("/api/action-protocol-catalog")
public class ActionProtocolCatalogController extends BaseController {
    @Operation(summary = "查询下游动作协议目录", description = "协议枚举值属于稳定线协议，因此保持英文")
    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("actionTypes", Arrays.stream(DownstreamActionType.values()).map(type -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", type.wireName());
            item.put("allowedSubActions", type.allowedSubActions().stream()
                    .map(DownstreamSubAction::wireName).collect(Collectors.toList()));
            return item;
        }).collect(Collectors.toList()));
        result.put("subActions", Arrays.stream(DownstreamSubAction.values())
                .map(DownstreamSubAction::wireName).collect(Collectors.toList()));
        result.put("subActionContracts", Arrays.stream(DownstreamSubAction.values()).map(subAction -> {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", subAction.wireName());
            item.put("requiredParameters", subAction.requiredParameters());
            return item;
        }).collect(Collectors.toList()));
        result.put("failureActions", Arrays.asList(PhaseFailureAction.values()));
        result.put("retryExhaustedActions", Arrays.asList(RetryExhaustedAction.values()));
        return success(result);
    }
}

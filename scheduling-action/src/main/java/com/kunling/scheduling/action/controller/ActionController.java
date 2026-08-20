package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.definition.application.SaveActionDefinitionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** 当前 Action 定义接口；不再暴露 clone、release、diff 等业务版本操作。 */
@Tag(name = "动作配置管理", description = "维护当前 Action 的步骤、参数约束及启停状态")
@RestController
@RequestMapping("/api/actions")
public class ActionController {
    private final ActionDefinitionService definitionService;

    public ActionController(ActionDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @Operation(summary = "查询全部 Action")
    @GetMapping
    public List<ActionDefinitionView> list() {
        return definitionService.list();
    }

    @Operation(summary = "查询 Action 详情")
    @GetMapping("/{actionKey}")
    public ActionDefinitionView get(
            @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
            @PathVariable String actionKey) {
        return definitionService.get(actionKey);
    }

    @Operation(summary = "新建 Action 草稿", description = "新建时 expectedRevision 必须为空")
    @PostMapping
    public ResponseEntity<ActionDefinitionView> create(@RequestBody SaveActionDefinitionRequest request) {
        if (request.expectedRevision() != null) {
            throw new IllegalArgumentException("新建 Action 不能携带 expectedRevision。");
        }
        ActionDefinitionView created = definitionService.create(request.definition());
        return ResponseEntity.created(URI.create("/api/actions/" + created.actionKey())).body(created);
    }

    @Operation(summary = "修改 Action 草稿", description = "修改后状态回到 DRAFT，执行中的 Action 不允许修改")
    @PutMapping("/{actionKey}")
    public ActionDefinitionView update(
                                       @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                       @PathVariable String actionKey,
                                       @RequestBody SaveActionDefinitionRequest request) {
        if (request.expectedRevision() == null) {
            throw new IllegalArgumentException("更新 Action 必须携带 expectedRevision。");
        }
        return definitionService.update(actionKey, request.expectedRevision(), request.definition());
    }

    @Operation(summary = "启用 Action", description = "启用前执行完整结构与参数绑定校验")
    @PostMapping("/{actionKey}/activate")
    public ActionDefinitionView activate(
                                         @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                         @PathVariable String actionKey,
                                         @Parameter(description = "当前并发控制 revision", example = "1")
                                         @RequestParam long expectedRevision) {
        return definitionService.activate(actionKey, expectedRevision);
    }

    @Operation(summary = "停用 Action")
    @PostMapping("/{actionKey}/disable")
    public ActionDefinitionView disable(
                                        @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                        @PathVariable String actionKey,
                                        @Parameter(description = "当前并发控制 revision", example = "1")
                                        @RequestParam long expectedRevision) {
        return definitionService.disable(actionKey, expectedRevision);
    }

    @Operation(summary = "删除 Action", description = "执行中的 Action 不允许删除")
    @DeleteMapping("/{actionKey}")
    public ResponseEntity<Void> delete(
                                       @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                       @PathVariable String actionKey,
                                       @Parameter(description = "当前并发控制 revision", example = "1")
                                       @RequestParam long expectedRevision) {
        definitionService.delete(actionKey, expectedRevision);
        return ResponseEntity.noContent().build();
    }
}

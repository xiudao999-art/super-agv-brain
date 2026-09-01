package com.kunling.scheduling.app.catalog;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 为其他页面提供数据库维护的 Action 业务场景目录。 */
@RestController
@RequestMapping("/api/action-business-scenes")
@Tag(name = "Action 业务场景目录", description = "查询业务场景及其可编排原子操作")
public class ActionSceneCatalogController extends BaseController {
    private final ActionSceneCatalogService catalogService;

    public ActionSceneCatalogController(ActionSceneCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "查询业务场景")
    public ApiResult<List<ActionSceneCatalogOption>> listBusinessScenes() {
        return success(catalogService.listBusinessScenes());
    }

    @GetMapping("/{sceneCode}/operations")
    @Operation(summary = "查询业务场景下的原子操作")
    public ApiResult<List<ActionSceneCatalogOption>> listOperations(
            @PathVariable("sceneCode") String sceneCode) {
        return success(catalogService.listOperations(sceneCode));
    }
}

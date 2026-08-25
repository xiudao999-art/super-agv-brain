package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.HomeOverviewResponse;
import com.kunling.scheduling.app.service.HomeOverviewTestService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行总览临时测试接口。
 *
 * <p>Controller 仅承担 HTTP 协议适配，测试数据读取及电量计算由应用服务负责。</p>
 */
@RestController
@RequestMapping("/api/home-test")
@Tag(name = "运行总览测试", description = "为运行总览页面提供 JSON 驱动的临时测试数据")
public class HomeTestController extends BaseController {

    private final HomeOverviewTestService overviewTestService;

    public HomeTestController(HomeOverviewTestService overviewTestService) {
        this.overviewTestService = overviewTestService;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询运行总览测试数据")
    public ApiResult<HomeOverviewResponse> getOverview() {
        return success(overviewTestService.getOverview());
    }
}

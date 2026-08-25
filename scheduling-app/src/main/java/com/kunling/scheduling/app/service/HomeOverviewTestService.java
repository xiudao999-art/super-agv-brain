package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.HomeOverviewResponse;
import com.kunling.scheduling.app.domain.HomeOverviewTestData;
import com.kunling.scheduling.app.mapper.HomeTestDataMapper;
import com.kunling.scheduling.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 组装运行总览测试数据，并按时间规则计算动态电量。 */
@Service
public class HomeOverviewTestService {

    private final HomeOverviewTestData testData;
    private final Clock clock;
    private final Instant batteryRuleStartedAt;

    @Autowired
    public HomeOverviewTestService(HomeTestDataMapper dataMapper) {
        this(dataMapper, Clock.systemUTC());
    }

    public HomeOverviewTestService(HomeTestDataMapper dataMapper, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.testData = validate(Objects.requireNonNull(
                dataMapper, "测试数据 Mapper 不能为空").load());
        // 临时测试数据不写数据库：每次服务启动后从 JSON 初始电量重新开始衰减。
        this.batteryRuleStartedAt = clock.instant();
    }

    public HomeOverviewResponse getOverview() {
        HomeOverviewTestData.AgvDefinition agv = testData.getAgvStatus();
        HomeOverviewTestData.TaskCompletionDefinition completion = testData.getTodayTaskCompletion();
        return new HomeOverviewResponse(
                new HomeOverviewResponse.AgvStatus(
                        agv.getAgvCode(),
                        agv.isOnline(),
                        agv.getExecutionStatus(),
                        calculateBatteryPercent(testData.getBatteryRule())
                ),
                testData.getCurrentOrder(),
                testData.getLocationConsistency(),
                new HomeOverviewResponse.TodayTaskCompletion(
                        completion.getCompletedCount(),
                        completion.getTotalCount(),
                        calculateCompletionRate(completion)
                ),
                testData.getHardwareModules()
        );
    }

    private int calculateBatteryPercent(HomeOverviewTestData.BatteryRule batteryRule) {
        long elapsedSeconds = Duration.between(batteryRuleStartedAt, clock.instant()).getSeconds();
        long elapsedMinutes = Math.max(0L, elapsedSeconds / 60L);
        long decreaseCount = elapsedMinutes / batteryRule.getDecreaseIntervalMinutes();
        long currentPercent = (long) batteryRule.getInitialPercent() - decreaseCount;
        return (int) Math.max(batteryRule.getMinimumPercent(), currentPercent);
    }

    private BigDecimal calculateCompletionRate(
            HomeOverviewTestData.TaskCompletionDefinition completion) {
        if (completion.getTotalCount() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completion.getCompletedCount())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(completion.getTotalCount()), 1, RoundingMode.HALF_UP);
    }

    private HomeOverviewTestData validate(HomeOverviewTestData data) {
        if (data == null) {
            throw invalidData("根对象不能为空");
        }

        HomeOverviewTestData.AgvDefinition agv = data.getAgvStatus();
        if (agv == null || !hasText(agv.getAgvCode()) || !hasText(agv.getExecutionStatus())) {
            throw invalidData("agvStatus.agvCode 和 executionStatus 不能为空");
        }

        HomeOverviewTestData.BatteryRule batteryRule = data.getBatteryRule();
        if (batteryRule == null
                || batteryRule.getInitialPercent() < 0
                || batteryRule.getInitialPercent() > 100
                || batteryRule.getMinimumPercent() < 0
                || batteryRule.getMinimumPercent() > batteryRule.getInitialPercent()
                || batteryRule.getDecreaseIntervalMinutes() <= 0) {
            throw invalidData("batteryRule 必须满足 0 <= minimumPercent <= initialPercent <= 100，"
                    + "且 decreaseIntervalMinutes > 0");
        }

        HomeOverviewResponse.CurrentOrder order = data.getCurrentOrder();
        if (order == null || order.getExecutingCount() < 0 || order.getQueuedCount() < 0
                || !hasText(order.getSource())) {
            throw invalidData("currentOrder 数量不能为负数，source 不能为空");
        }

        HomeOverviewResponse.LocationConsistency consistency = data.getLocationConsistency();
        if (consistency == null || consistency.getRate() == null
                || consistency.getRate().compareTo(BigDecimal.ZERO) < 0
                || consistency.getRate().compareTo(BigDecimal.valueOf(100)) > 0
                || consistency.getPendingConfirmationCount() < 0) {
            throw invalidData("locationConsistency.rate 必须在 0 到 100 之间，待确认数不能为负数");
        }

        HomeOverviewTestData.TaskCompletionDefinition completion = data.getTodayTaskCompletion();
        if (completion == null || completion.getCompletedCount() < 0
                || completion.getTotalCount() < 0
                || completion.getCompletedCount() > completion.getTotalCount()) {
            throw invalidData("todayTaskCompletion 必须满足 0 <= completedCount <= totalCount");
        }

        validateHardwareModules(data.getHardwareModules());
        return data;
    }

    private void validateHardwareModules(List<HomeOverviewResponse.HardwareModuleStatus> modules) {
        if (modules == null || modules.isEmpty()) {
            throw invalidData("hardwareModules 不能为空");
        }
        Set<String> moduleCodes = new HashSet<>();
        for (HomeOverviewResponse.HardwareModuleStatus module : modules) {
            if (module == null || !hasText(module.getCode()) || !hasText(module.getName())) {
                throw invalidData("硬件模组的 code 和 name 不能为空");
            }
            if (!moduleCodes.add(module.getCode())) {
                throw invalidData("硬件模组 code 不能重复：" + module.getCode());
            }
        }
    }

    private ServiceUnavailableException invalidData(String reason) {
        return new ServiceUnavailableException("运行总览测试数据配置无效：" + reason);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

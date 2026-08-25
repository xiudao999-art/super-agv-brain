package com.kunling.scheduling.app.hometest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * resource JSON 对应的内部配置模型。
 *
 * <p>该模型不会直接暴露给前端，避免把电量衰减参数混入接口响应。</p>
 */
final class HomeOverviewTestData {

    private final AgvDefinition agvStatus;
    private final BatteryRule batteryRule;
    private final HomeOverviewResponse.CurrentOrder currentOrder;
    private final HomeOverviewResponse.LocationConsistency locationConsistency;
    private final TaskCompletionDefinition todayTaskCompletion;
    private final List<HomeOverviewResponse.HardwareModuleStatus> hardwareModules;

    @JsonCreator
    HomeOverviewTestData(
            @JsonProperty("agvStatus") AgvDefinition agvStatus,
            @JsonProperty("batteryRule") BatteryRule batteryRule,
            @JsonProperty("currentOrder") HomeOverviewResponse.CurrentOrder currentOrder,
            @JsonProperty("locationConsistency") HomeOverviewResponse.LocationConsistency locationConsistency,
            @JsonProperty("todayTaskCompletion") TaskCompletionDefinition todayTaskCompletion,
            @JsonProperty("hardwareModules") List<HomeOverviewResponse.HardwareModuleStatus> hardwareModules) {
        this.agvStatus = agvStatus;
        this.batteryRule = batteryRule;
        this.currentOrder = currentOrder;
        this.locationConsistency = locationConsistency;
        this.todayTaskCompletion = todayTaskCompletion;
        this.hardwareModules = hardwareModules == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(hardwareModules));
    }

    AgvDefinition getAgvStatus() {
        return agvStatus;
    }

    BatteryRule getBatteryRule() {
        return batteryRule;
    }

    HomeOverviewResponse.CurrentOrder getCurrentOrder() {
        return currentOrder;
    }

    HomeOverviewResponse.LocationConsistency getLocationConsistency() {
        return locationConsistency;
    }

    TaskCompletionDefinition getTodayTaskCompletion() {
        return todayTaskCompletion;
    }

    List<HomeOverviewResponse.HardwareModuleStatus> getHardwareModules() {
        return hardwareModules;
    }

    static final class AgvDefinition {
        private final String agvCode;
        private final boolean online;
        private final String executionStatus;

        @JsonCreator
        AgvDefinition(@JsonProperty("agvCode") String agvCode,
                      @JsonProperty("online") boolean online,
                      @JsonProperty("executionStatus") String executionStatus) {
            this.agvCode = agvCode;
            this.online = online;
            this.executionStatus = executionStatus;
        }

        String getAgvCode() {
            return agvCode;
        }

        boolean isOnline() {
            return online;
        }

        String getExecutionStatus() {
            return executionStatus;
        }
    }

    static final class BatteryRule {
        private final int initialPercent;
        private final int decreaseIntervalMinutes;
        private final int minimumPercent;

        @JsonCreator
        BatteryRule(@JsonProperty("initialPercent") int initialPercent,
                    @JsonProperty("decreaseIntervalMinutes") int decreaseIntervalMinutes,
                    @JsonProperty("minimumPercent") int minimumPercent) {
            this.initialPercent = initialPercent;
            this.decreaseIntervalMinutes = decreaseIntervalMinutes;
            this.minimumPercent = minimumPercent;
        }

        int getInitialPercent() {
            return initialPercent;
        }

        int getDecreaseIntervalMinutes() {
            return decreaseIntervalMinutes;
        }

        int getMinimumPercent() {
            return minimumPercent;
        }
    }

    static final class TaskCompletionDefinition {
        private final int completedCount;
        private final int totalCount;

        @JsonCreator
        TaskCompletionDefinition(@JsonProperty("completedCount") int completedCount,
                                 @JsonProperty("totalCount") int totalCount) {
            this.completedCount = completedCount;
            this.totalCount = totalCount;
        }

        int getCompletedCount() {
            return completedCount;
        }

        int getTotalCount() {
            return totalCount;
        }
    }
}

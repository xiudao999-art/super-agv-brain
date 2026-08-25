package com.kunling.scheduling.app.domain;

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
public final class HomeOverviewTestData {

    private final AgvDefinition agvStatus;
    private final BatteryRule batteryRule;
    private final HomeOverviewResponse.CurrentOrder currentOrder;
    private final HomeOverviewResponse.LocationConsistency locationConsistency;
    private final TaskCompletionDefinition todayTaskCompletion;
    private final List<HomeOverviewResponse.HardwareModuleStatus> hardwareModules;

    @JsonCreator
    public HomeOverviewTestData(
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

    public AgvDefinition getAgvStatus() {
        return agvStatus;
    }

    public BatteryRule getBatteryRule() {
        return batteryRule;
    }

    public HomeOverviewResponse.CurrentOrder getCurrentOrder() {
        return currentOrder;
    }

    public HomeOverviewResponse.LocationConsistency getLocationConsistency() {
        return locationConsistency;
    }

    public TaskCompletionDefinition getTodayTaskCompletion() {
        return todayTaskCompletion;
    }

    public List<HomeOverviewResponse.HardwareModuleStatus> getHardwareModules() {
        return hardwareModules;
    }

    public static final class AgvDefinition {
        private final String agvCode;
        private final boolean online;
        private final String executionStatus;

        @JsonCreator
        public AgvDefinition(@JsonProperty("agvCode") String agvCode,
                      @JsonProperty("online") boolean online,
                      @JsonProperty("executionStatus") String executionStatus) {
            this.agvCode = agvCode;
            this.online = online;
            this.executionStatus = executionStatus;
        }

        public String getAgvCode() {
            return agvCode;
        }

        public boolean isOnline() {
            return online;
        }

        public String getExecutionStatus() {
            return executionStatus;
        }
    }

    public static final class BatteryRule {
        private final int initialPercent;
        private final int decreaseIntervalMinutes;
        private final int minimumPercent;

        @JsonCreator
        public BatteryRule(@JsonProperty("initialPercent") int initialPercent,
                    @JsonProperty("decreaseIntervalMinutes") int decreaseIntervalMinutes,
                    @JsonProperty("minimumPercent") int minimumPercent) {
            this.initialPercent = initialPercent;
            this.decreaseIntervalMinutes = decreaseIntervalMinutes;
            this.minimumPercent = minimumPercent;
        }

        public int getInitialPercent() {
            return initialPercent;
        }

        public int getDecreaseIntervalMinutes() {
            return decreaseIntervalMinutes;
        }

        public int getMinimumPercent() {
            return minimumPercent;
        }
    }

    public static final class TaskCompletionDefinition {
        private final int completedCount;
        private final int totalCount;

        @JsonCreator
        public TaskCompletionDefinition(@JsonProperty("completedCount") int completedCount,
                                 @JsonProperty("totalCount") int totalCount) {
            this.completedCount = completedCount;
            this.totalCount = totalCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }
    }
}

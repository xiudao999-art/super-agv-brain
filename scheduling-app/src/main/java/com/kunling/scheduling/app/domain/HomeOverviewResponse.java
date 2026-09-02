package com.kunling.scheduling.app.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 运行总览页面所需的测试数据。 */
@Schema(description = "运行总览测试数据")
public final class HomeOverviewResponse {

    private final AgvStatus agvStatus;
    private final AgvStatistics agvStatistics;
    private final CurrentOrder currentOrder;
    private final OrderStatistics orderStatistics;
    private final LocationConsistency locationConsistency;
    private final TodayTaskCompletion todayTaskCompletion;
    private final List<HardwareModuleStatus> hardwareModules;

    public HomeOverviewResponse(AgvStatus agvStatus,
                                AgvStatistics agvStatistics,
                                CurrentOrder currentOrder,
                                OrderStatistics orderStatistics,
                                LocationConsistency locationConsistency,
                                TodayTaskCompletion todayTaskCompletion,
                                List<HardwareModuleStatus> hardwareModules) {
        this.agvStatus = Objects.requireNonNull(agvStatus, "AGV 状态不能为空");
        this.agvStatistics = Objects.requireNonNull(agvStatistics, "AGV 统计数据不能为空");
        this.currentOrder = Objects.requireNonNull(currentOrder, "当前订单数据不能为空");
        this.orderStatistics = Objects.requireNonNull(orderStatistics, "订单统计数据不能为空");
        this.locationConsistency = Objects.requireNonNull(
                locationConsistency, "库位一致性数据不能为空");
        this.todayTaskCompletion = Objects.requireNonNull(
                todayTaskCompletion, "今日任务完成数据不能为空");
        this.hardwareModules = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(hardwareModules, "硬件模组状态不能为空")));
    }

    public AgvStatus getAgvStatus() {
        return agvStatus;
    }

    public AgvStatistics getAgvStatistics() {
        return agvStatistics;
    }

    /** 首页 AGV 总数及各运行状态数量。 */
    public static final class AgvStatistics {
        private final long totalCount;
        private final long runningCount;
        private final long idleWaitingCount;
        private final long chargingCount;
        private final long abnormalCount;

        public AgvStatistics(long totalCount, long runningCount, long idleWaitingCount,
                             long chargingCount, long abnormalCount) {
            this.totalCount = totalCount;
            this.runningCount = runningCount;
            this.idleWaitingCount = idleWaitingCount;
            this.chargingCount = chargingCount;
            this.abnormalCount = abnormalCount;
        }

        public long getTotalCount() { return totalCount; }
        public long getRunningCount() { return runningCount; }
        public long getIdleWaitingCount() { return idleWaitingCount; }
        public long getChargingCount() { return chargingCount; }
        public long getAbnormalCount() { return abnormalCount; }
    }

    public CurrentOrder getCurrentOrder() {
        return currentOrder;
    }

    public OrderStatistics getOrderStatistics() {
        return orderStatistics;
    }

    /** 今日接收及当前各状态订单数量。 */
    public static final class OrderStatistics {
        private final long todayReceivedCount;
        private final long runningCount;
        private final long queuedCount;
        private final long completedCount;
        private final long abnormalCount;
        private final String sources;

        public OrderStatistics(long todayReceivedCount, long runningCount, long queuedCount,
                               long completedCount, long abnormalCount, String sources) {
            this.todayReceivedCount = todayReceivedCount;
            this.runningCount = runningCount;
            this.queuedCount = queuedCount;
            this.completedCount = completedCount;
            this.abnormalCount = abnormalCount;
            this.sources = sources;
        }

        public long getTodayReceivedCount() { return todayReceivedCount; }
        public long getRunningCount() { return runningCount; }
        public long getQueuedCount() { return queuedCount; }
        public long getCompletedCount() { return completedCount; }
        public long getAbnormalCount() { return abnormalCount; }
        public String getSources() { return sources; }
    }

    public LocationConsistency getLocationConsistency() {
        return locationConsistency;
    }

    public TodayTaskCompletion getTodayTaskCompletion() {
        return todayTaskCompletion;
    }

    public List<HardwareModuleStatus> getHardwareModules() {
        return hardwareModules;
    }

    /** AGV 的连接、作业和电量状态。 */
    public static final class AgvStatus {
        private final String agvCode;
        private final boolean online;
        private final String executionStatus;
        private final int batteryPercent;

        public AgvStatus(String agvCode, boolean online, String executionStatus, int batteryPercent) {
            this.agvCode = agvCode;
            this.online = online;
            this.executionStatus = executionStatus;
            this.batteryPercent = batteryPercent;
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

        public int getBatteryPercent() {
            return batteryPercent;
        }
    }

    /** 当前执行及排队订单的数量。 */
    public static final class CurrentOrder {
        private final int executingCount;
        private final int queuedCount;
        private final String source;

        @JsonCreator
        public CurrentOrder(@JsonProperty("executingCount") int executingCount,
                            @JsonProperty("queuedCount") int queuedCount,
                            @JsonProperty("source") String source) {
            this.executingCount = executingCount;
            this.queuedCount = queuedCount;
            this.source = source;
        }

        public int getExecutingCount() {
            return executingCount;
        }

        public int getQueuedCount() {
            return queuedCount;
        }

        public String getSource() {
            return source;
        }
    }

    /** 库位一致率及待确认数量。 */
    public static final class LocationConsistency {
        private final BigDecimal rate;
        private final int pendingConfirmationCount;

        @JsonCreator
        public LocationConsistency(@JsonProperty("rate") BigDecimal rate,
                                   @JsonProperty("pendingConfirmationCount") int pendingConfirmationCount) {
            this.rate = rate;
            this.pendingConfirmationCount = pendingConfirmationCount;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public int getPendingConfirmationCount() {
            return pendingConfirmationCount;
        }
    }

    /** 今日任务完成数量及由数量计算出的完成率。 */
    public static final class TodayTaskCompletion {
        private final int completedCount;
        private final int totalCount;
        private final BigDecimal completionRate;

        public TodayTaskCompletion(int completedCount, int totalCount, BigDecimal completionRate) {
            this.completedCount = completedCount;
            this.totalCount = totalCount;
            this.completionRate = completionRate;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public BigDecimal getCompletionRate() {
            return completionRate;
        }
    }

    /** 单个硬件模组的在线状态；图标仍由前端根据 code 选择。 */
    public static final class HardwareModuleStatus {
        private final String code;
        private final String name;
        private final boolean online;

        @JsonCreator
        public HardwareModuleStatus(@JsonProperty("code") String code,
                                    @JsonProperty("name") String name,
                                    @JsonProperty("online") boolean online) {
            this.code = code;
            this.name = name;
            this.online = online;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public boolean isOnline() {
            return online;
        }
    }
}

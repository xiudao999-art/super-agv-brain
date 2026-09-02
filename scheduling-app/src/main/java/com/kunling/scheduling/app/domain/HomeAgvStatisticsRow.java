package com.kunling.scheduling.app.domain;

/** 首页 AGV 运行状态数据库统计结果。 */
public class HomeAgvStatisticsRow {

    private long totalCount;
    private long runningCount;
    private long idleWaitingCount;
    private long chargingCount;
    private long abnormalCount;

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getRunningCount() { return runningCount; }
    public void setRunningCount(long runningCount) { this.runningCount = runningCount; }
    public long getIdleWaitingCount() { return idleWaitingCount; }
    public void setIdleWaitingCount(long idleWaitingCount) { this.idleWaitingCount = idleWaitingCount; }
    public long getChargingCount() { return chargingCount; }
    public void setChargingCount(long chargingCount) { this.chargingCount = chargingCount; }
    public long getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(long abnormalCount) { this.abnormalCount = abnormalCount; }
}

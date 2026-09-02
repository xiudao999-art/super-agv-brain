package com.kunling.scheduling.app.domain;

/** 首页订单统计SQL内部映射对象。 */
public class HomeOrderStatisticsRow {
    private long todayReceivedCount;
    private long runningCount;
    private long queuedCount;
    private long completedCount;
    private long abnormalCount;
    private String sources;

    public long getTodayReceivedCount() { return todayReceivedCount; }
    public void setTodayReceivedCount(long value) { this.todayReceivedCount = value; }
    public long getRunningCount() { return runningCount; }
    public void setRunningCount(long value) { this.runningCount = value; }
    public long getQueuedCount() { return queuedCount; }
    public void setQueuedCount(long value) { this.queuedCount = value; }
    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long value) { this.completedCount = value; }
    public long getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(long value) { this.abnormalCount = value; }
    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }
}

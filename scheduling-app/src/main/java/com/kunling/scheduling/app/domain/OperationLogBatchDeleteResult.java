package com.kunling.scheduling.app.domain;

/** 批量删除结果，便于前端识别不存在或已被删除的日志。 */
public final class OperationLogBatchDeleteResult {

    private final int requestedCount;
    private final int deletedCount;

    public OperationLogBatchDeleteResult(int requestedCount, int deletedCount) {
        this.requestedCount = requestedCount;
        this.deletedCount = deletedCount;
    }

    public int getRequestedCount() { return requestedCount; }
    public int getDeletedCount() { return deletedCount; }
}

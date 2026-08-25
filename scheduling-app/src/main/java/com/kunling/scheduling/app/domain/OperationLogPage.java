package com.kunling.scheduling.app.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 操作日志分页响应。 */
public final class OperationLogPage {

    private final long total;
    private final long pageNum;
    private final int pageSize;
    private final List<SystemOperationLog> records;

    public OperationLogPage(long total, long pageNum, int pageSize,
                            List<SystemOperationLog> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    public long getTotal() { return total; }
    public long getPageNum() { return pageNum; }
    public int getPageSize() { return pageSize; }
    public List<SystemOperationLog> getRecords() { return records; }
}

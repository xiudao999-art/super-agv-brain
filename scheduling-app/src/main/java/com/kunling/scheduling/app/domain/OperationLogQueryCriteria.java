package com.kunling.scheduling.app.domain;

import com.kunling.scheduling.common.audit.OperationType;

import java.time.LocalDateTime;
import java.util.Locale;

/** 经过校验和标准化的操作日志分页查询条件。 */
public final class OperationLogQueryCriteria {

    public static final int MAX_PAGE_SIZE = 100;

    private final long pageNum;
    private final int pageSize;
    private final String module;
    private final OperationType operationType;
    private final OperationLogStatus status;
    private final String requestMethod;
    private final String keyword;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public OperationLogQueryCriteria(long pageNum, int pageSize, String module,
                                     OperationType operationType, OperationLogStatus status,
                                     String requestMethod, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 不能小于 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 范围必须为 1 到 " + MAX_PAGE_SIZE);
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime 不能晚于 endTime");
        }
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.module = normalize(module, 64, "module");
        this.operationType = operationType;
        this.status = status;
        this.requestMethod = normalizeRequestMethod(requestMethod);
        this.keyword = normalize(keyword, 128, "keyword");
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public long offset() {
        long pageIndex = pageNum - 1;
        if (pageIndex > Long.MAX_VALUE / pageSize) {
            throw new IllegalArgumentException("分页偏移量超出允许范围");
        }
        return pageIndex * pageSize;
    }

    private String normalizeRequestMethod(String value) {
        String normalized = normalize(value, 16, "requestMethod");
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]+")) {
            throw new IllegalArgumentException("requestMethod 格式不合法");
        }
        return normalized;
    }

    private String normalize(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    public long getPageNum() { return pageNum; }
    public int getPageSize() { return pageSize; }
    public String getModule() { return module; }
    public OperationType getOperationType() { return operationType; }
    public OperationLogStatus getStatus() { return status; }
    public String getRequestMethod() { return requestMethod; }
    public String getKeyword() { return keyword; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}

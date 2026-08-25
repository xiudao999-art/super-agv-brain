package com.kunling.scheduling.app.domain;

import com.kunling.scheduling.common.audit.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/** 前端操作日志分页与筛选参数。 */
public class OperationLogQueryRequest {

    @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private long pageNum = 1;

    @Min(1)
    @Max(OperationLogQueryCriteria.MAX_PAGE_SIZE)
    @Schema(description = "每页数量，最大 100", defaultValue = "20")
    private int pageSize = 20;

    @Size(max = 64)
    @Schema(description = "业务模块，精确匹配", example = "动作执行")
    private String module;

    @Schema(description = "操作类型")
    private OperationType operationType;

    @Schema(description = "执行结果")
    private OperationLogStatus status;

    @Pattern(regexp = "^$|^[A-Za-z]{1,16}$")
    @Schema(description = "HTTP 请求方法", example = "POST")
    private String requestMethod;

    @Size(max = 128)
    @Schema(description = "模糊搜索模块、操作名称、处理方法、请求路径和异常消息")
    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "操作开始时间，ISO-8601 格式", example = "2026-08-25T00:00:00")
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "操作结束时间，ISO-8601 格式", example = "2026-08-25T23:59:59")
    private LocalDateTime endTime;

    public OperationLogQueryCriteria toCriteria() {
        return new OperationLogQueryCriteria(pageNum, pageSize, module, operationType, status,
                requestMethod, keyword, startTime, endTime);
    }

    public long getPageNum() { return pageNum; }
    public void setPageNum(long pageNum) { this.pageNum = pageNum; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public OperationType getOperationType() { return operationType; }
    public void setOperationType(OperationType operationType) { this.operationType = operationType; }
    public OperationLogStatus getStatus() { return status; }
    public void setStatus(OperationLogStatus status) { this.status = status; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}

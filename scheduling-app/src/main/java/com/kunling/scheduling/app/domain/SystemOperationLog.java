package com.kunling.scheduling.app.domain;

import com.kunling.scheduling.common.audit.OperationType;

import java.time.LocalDateTime;

/** 系统重要业务操作日志实体，对应 system_operation_log 表。 */
public class SystemOperationLog {

    private Long id;
    private String module;
    private String operation;
    private OperationType operationType;
    private String handlerMethod;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private String responseBody;
    private OperationLogStatus status;
    private String errorMessage;
    private LocalDateTime operatedAt;
    private long durationMs;

    public SystemOperationLog() {
    }

    public SystemOperationLog(String module, String operation, OperationType operationType,
                              String handlerMethod, String requestMethod, String requestUri,
                              String requestParams, String responseBody, OperationLogStatus status,
                              String errorMessage, LocalDateTime operatedAt, long durationMs) {
        this.module = module;
        this.operation = operation;
        this.operationType = operationType;
        this.handlerMethod = handlerMethod;
        this.requestMethod = requestMethod;
        this.requestUri = requestUri;
        this.requestParams = requestParams;
        this.responseBody = responseBody;
        this.status = status;
        this.errorMessage = errorMessage;
        this.operatedAt = operatedAt;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public OperationType getOperationType() { return operationType; }
    public void setOperationType(OperationType operationType) { this.operationType = operationType; }
    public String getHandlerMethod() { return handlerMethod; }
    public void setHandlerMethod(String handlerMethod) { this.handlerMethod = handlerMethod; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public OperationLogStatus getStatus() { return status; }
    public void setStatus(OperationLogStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}

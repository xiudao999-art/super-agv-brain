package com.kunling.scheduling.app.service.aspect;

import com.kunling.scheduling.app.domain.OperationLogStatus;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.app.service.OperationLogWriter;
import com.kunling.scheduling.common.audit.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** 在重要业务方法边界采集请求结果、异常和耗时。 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogWriter writer;
    private final OperationLogPayloadSerializer payloadSerializer;
    private final Clock clock;

    @Autowired
    public OperationLogAspect(OperationLogWriter writer,
                              OperationLogPayloadSerializer payloadSerializer) {
        this(writer, payloadSerializer, Clock.systemDefaultZone());
    }

    OperationLogAspect(OperationLogWriter writer,
                       OperationLogPayloadSerializer payloadSerializer,
                       Clock clock) {
        this.writer = writer;
        this.payloadSerializer = payloadSerializer;
        this.clock = clock;
    }

    @Around("@annotation(operationLog)")
    public Object record(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        LocalDateTime operatedAt = LocalDateTime.now(clock);
        HttpServletRequest request = currentRequest();
        String requestParams = request == null
                ? null
                : payloadSerializer.serializeRequest(joinPoint, operationLog, request);
        long startedNanos = System.nanoTime();
        try {
            Object response = joinPoint.proceed();
            long durationMs = elapsedMillis(startedNanos);
            persist(joinPoint, operationLog, request, requestParams,
                    payloadSerializer.serializeResponse(response, operationLog),
                    OperationLogStatus.SUCCESS, null, operatedAt, durationMs);
            return response;
        } catch (Throwable exception) {
            long durationMs = elapsedMillis(startedNanos);
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            persist(joinPoint, operationLog, request, requestParams, null,
                    OperationLogStatus.FAILURE, payloadSerializer.truncateError(message),
                    operatedAt, durationMs);
            throw exception;
        }
    }

    private void persist(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                         HttpServletRequest request, String requestParams, String responseBody,
                         OperationLogStatus status, String errorMessage,
                         LocalDateTime operatedAt, long durationMs) {
        try {
            SystemOperationLog entry = new SystemOperationLog(
                    limit(operationLog.module(), 64),
                    limit(operationLog.operation(), 128),
                    operationLog.type(),
                    limit(handlerMethod(joinPoint), 255),
                    limit(request == null ? "" : request.getMethod(), 16),
                    limit(request == null ? "" : request.getRequestURI(), 512),
                    requestParams,
                    responseBody,
                    status,
                    errorMessage,
                    operatedAt,
                    durationMs);
            writer.write(entry);
        } catch (RuntimeException writeException) {
            // 日志基础设施故障不能改变原业务接口的成功或失败结果。
            LOGGER.error("系统操作日志写入失败: {}",
                    joinPoint.getSignature().toShortString(), writeException);
        }
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    private String handlerMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetType = AopUtils.getTargetClass(joinPoint.getTarget());
        return targetType.getName() + "." + signature.getMethod().getName();
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

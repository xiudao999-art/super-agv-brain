package com.kunling.scheduling.app.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.app.domain.OperationLogStatus;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.app.service.OperationLogWriter;
import com.kunling.scheduling.app.service.aspect.OperationLogAspect;
import com.kunling.scheduling.app.service.aspect.OperationLogPayloadSerializer;
import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationLogAspectTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 成功操作会记录脱敏参数响应和固定时间() {
        OperationLogWriter writer = mock(OperationLogWriter.class);
        AuditedOperations proxy = createProxy(writer);
        bindRequest();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("taskName", "搬运任务");
        requestBody.put("password", "plain-password");
        requestBody.put("userId", 99L);
        requestBody.put("email", "operator@example.com");
        Map<String, Object> response = proxy.create(requestBody);

        assertThat(response).containsEntry("result", "ok");
        ArgumentCaptor<SystemOperationLog> captor = ArgumentCaptor.forClass(SystemOperationLog.class);
        verify(writer).write(captor.capture());
        SystemOperationLog entry = captor.getValue();
        assertThat(entry.getModule()).isEqualTo("测试模块");
        assertThat(entry.getOperationType()).isEqualTo(OperationType.CREATE);
        assertThat(entry.getRequestMethod()).isEqualTo("POST");
        assertThat(entry.getRequestUri()).isEqualTo("/api/test/42");
        assertThat(entry.getRequestParams())
                .contains("搬运任务", "***", "keyword")
                .doesNotContain("plain-password", "operator@example.com", "secret-token");
        assertThat(entry.getResponseBody())
                .contains("result")
                .doesNotContain("response-token");
        assertThat(entry.getStatus()).isEqualTo(OperationLogStatus.SUCCESS);
        assertThat(entry.getOperatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 25, 10, 0));
        assertThat(entry.getDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void 业务异常会记录失败但保持原异常语义() {
        OperationLogWriter writer = mock(OperationLogWriter.class);
        AuditedOperations proxy = createProxy(writer);
        bindRequest();

        assertThatThrownBy(() -> proxy.fail(Collections.singletonMap("reason", "test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("业务执行失败");

        ArgumentCaptor<SystemOperationLog> captor = ArgumentCaptor.forClass(SystemOperationLog.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OperationLogStatus.FAILURE);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("业务执行失败");
        assertThat(captor.getValue().getResponseBody()).isNull();
    }

    @Test
    void 日志写入失败不能改变业务返回结果() {
        OperationLogWriter writer = mock(OperationLogWriter.class);
        doThrow(new IllegalStateException("日志表不可用")).when(writer).write(any(SystemOperationLog.class));
        AuditedOperations proxy = createProxy(writer);
        bindRequest();

        assertThat(proxy.create(Collections.singletonMap("taskName", "任务")))
                .containsEntry("result", "ok");
    }

    private AuditedOperations createProxy(OperationLogWriter writer) {
        OperationLogPayloadSerializer serializer = new OperationLogPayloadSerializer(new ObjectMapper());
        OperationLogAspect aspect = new OperationLogAspect(
                writer,
                serializer,
                Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC));
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new AuditedOperations());
        proxyFactory.addAspect(aspect);
        return proxyFactory.getProxy();
    }

    private void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test/42");
        request.setParameter("keyword", "MOVE");
        request.setParameter("token", "secret-token");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Collections.singletonMap("id", "42"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    static class AuditedOperations {

        @OperationLog(module = "测试模块", operation = "新建测试任务", type = OperationType.CREATE)
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("result", "ok");
            response.put("accessToken", "response-token");
            return response;
        }

        @OperationLog(module = "测试模块", operation = "失败测试", type = OperationType.EXECUTE)
        public void fail(@RequestBody Map<String, Object> body) {
            throw new IllegalStateException("业务执行失败");
        }
    }
}

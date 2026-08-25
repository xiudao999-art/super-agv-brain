package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.OperationLogBatchDeleteResult;
import com.kunling.scheduling.app.domain.OperationLogPage;
import com.kunling.scheduling.app.domain.OperationLogQueryCriteria;
import com.kunling.scheduling.app.domain.OperationLogStatus;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.app.service.OperationLogService;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationLogControllerTest {

    private OperationLogService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OperationLogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OperationLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Collections.emptyList()))
                .build();
    }

    @Test
    void 分页接口绑定筛选条件并返回统一分页结构() throws Exception {
        SystemOperationLog record = new SystemOperationLog(
                "动作执行", "开始执行完整动作包", OperationType.EXECUTE,
                "ExecutionController.start", "POST", "/api/action-executions",
                "{\"body\":{}}", null, OperationLogStatus.SUCCESS, null,
                LocalDateTime.of(2026, 8, 25, 18, 0), 35L);
        record.setId(15L);
        when(service.page(any(OperationLogQueryCriteria.class)))
                .thenReturn(new OperationLogPage(1L, 2L, 20, Collections.singletonList(record)));

        mockMvc.perform(get("/api/system-operation-logs")
                        .param("pageNum", "2")
                        .param("pageSize", "20")
                        .param("module", "动作执行")
                        .param("operationType", "EXECUTE")
                        .param("status", "SUCCESS")
                        .param("requestMethod", "post")
                        .param("keyword", "action-executions")
                        .param("startTime", "2026-08-25T00:00:00")
                        .param("endTime", "2026-08-25T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(15))
                .andExpect(jsonPath("$.data.records[0].operationType").value("EXECUTE"))
                .andExpect(jsonPath("$.data.records[0].status").value("SUCCESS"));

        ArgumentCaptor<OperationLogQueryCriteria> captor =
                ArgumentCaptor.forClass(OperationLogQueryCriteria.class);
        verify(service).page(captor.capture());
        assertThat(captor.getValue().getRequestMethod()).isEqualTo("POST");
        assertThat(captor.getValue().getModule()).isEqualTo("动作执行");
        assertThat(captor.getValue().getStartTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0));
    }

    @Test
    void 批量删除接口返回请求数量和实际删除数量() throws Exception {
        when(service.deleteBatch(anyList())).thenReturn(new OperationLogBatchDeleteResult(2, 1));

        mockMvc.perform(delete("/api/system-operation-logs/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[7,9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.deletedCount").value(1));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).deleteBatch(captor.capture());
        assertThat(captor.getValue()).containsExactly(7L, 9L);
    }

    @Test
    void 分页接口拒绝超过上限的pageSize() throws Exception {
        mockMvc.perform(get("/api/system-operation-logs").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}

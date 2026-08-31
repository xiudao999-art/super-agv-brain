package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.ActionParameterSchema;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterOwnerType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.SaveRequest;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationResult;
import com.kunling.scheduling.app.service.ActionParameterSchemaService;
import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionParameterSchemaControllerTest {

    private ActionParameterSchemaService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ActionParameterSchemaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActionParameterSchemaController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Collections.emptyList()))
                .build();
    }

    @Test
    void 查询接口返回完整所有者和空字段() throws Exception {
        when(service.get(ParameterOwnerType.MAIN_ACTION, "ACTION-MOVE"))
                .thenReturn(new ActionParameterSchema(
                        ParameterOwnerType.MAIN_ACTION, "ACTION-MOVE", Collections.emptyList()));

        mockMvc.perform(get("/api/action-parameter-schemas/MAIN_ACTION/ACTION-MOVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ownerType").value("MAIN_ACTION"))
                .andExpect(jsonPath("$.data.ownerKey").value("ACTION-MOVE"))
                .andExpect(jsonPath("$.data.fields").isEmpty());
    }

    @Test
    void 保存接口只接收完整字段集合并以路径所有者为准() throws Exception {
        when(service.save(org.mockito.ArgumentMatchers.eq(ParameterOwnerType.SUB_ACTION),
                org.mockito.ArgumentMatchers.eq("MOVE_TO_MAP_POINT"),
                org.mockito.ArgumentMatchers.any(SaveRequest.class)))
                .thenReturn(new ActionParameterSchema(ParameterOwnerType.SUB_ACTION,
                        "MOVE_TO_MAP_POINT", Collections.emptyList()));

        mockMvc.perform(put("/api/action-parameter-schemas/SUB_ACTION/MOVE_TO_MAP_POINT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerType").value("SUB_ACTION"))
                .andExpect(jsonPath("$.data.fields").isEmpty());

        ArgumentCaptor<SaveRequest> captor = ArgumentCaptor.forClass(SaveRequest.class);
        verify(service).save(org.mockito.ArgumentMatchers.eq(ParameterOwnerType.SUB_ACTION),
                org.mockito.ArgumentMatchers.eq("MOVE_TO_MAP_POINT"), captor.capture());
        assertThat(captor.getValue().getFields()).isEmpty();
    }

    @Test
    void 校验接口返回结构化结果且非法ownerType返回400() throws Exception {
        when(service.validate(org.mockito.ArgumentMatchers.eq(ParameterOwnerType.SUB_ACTION),
                org.mockito.ArgumentMatchers.eq("MOVE_TO_POSE"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ValidationResult(Collections.emptyList()));

        mockMvc.perform(post("/api/action-parameter-schemas/SUB_ACTION/MOVE_TO_POSE/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pose\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.issues").isEmpty());

        mockMvc.perform(get("/api/action-parameter-schemas/main_action/ACTION-MOVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}

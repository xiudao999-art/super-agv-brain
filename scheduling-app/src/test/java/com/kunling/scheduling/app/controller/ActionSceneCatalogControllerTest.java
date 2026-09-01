package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.ActionSceneCatalogOption;
import com.kunling.scheduling.app.service.ActionSceneCatalogService;
import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionSceneCatalogControllerTest {

    private ActionSceneCatalogService catalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalogService = mock(ActionSceneCatalogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActionSceneCatalogController(catalogService))
                .setControllerAdvice(new GlobalExceptionHandler(Collections.emptyList()))
                .build();
    }

    @Test
    void 查询业务场景返回统一响应和可展示对象() throws Exception {
        when(catalogService.listBusinessScenes()).thenReturn(Arrays.asList(
                new ActionSceneCatalogOption("HOME", "回零"),
                new ActionSceneCatalogOption("PICK", "抓取")));

        mockMvc.perform(get("/api/action-business-scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data[0].code").value("HOME"))
                .andExpect(jsonPath("$.data[0].name").value("回零"))
                .andExpect(jsonPath("$.data[1].code").value("PICK"))
                .andExpect(jsonPath("$.data[1].name").value("抓取"));
    }

    @Test
    void 查询场景原子操作返回统一响应和可展示对象() throws Exception {
        when(catalogService.listOperations("pick")).thenReturn(Arrays.asList(
                new ActionSceneCatalogOption("MOVE_TO_POSE", "移动到位姿"),
                new ActionSceneCatalogOption("GRIP.OPEN", "夹爪打开")));

        mockMvc.perform(get("/api/action-business-scenes/pick/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data[0].code").value("MOVE_TO_POSE"))
                .andExpect(jsonPath("$.data[0].name").value("移动到位姿"))
                .andExpect(jsonPath("$.data[1].code").value("GRIP.OPEN"))
                .andExpect(jsonPath("$.data[1].name").value("夹爪打开"));
    }
}

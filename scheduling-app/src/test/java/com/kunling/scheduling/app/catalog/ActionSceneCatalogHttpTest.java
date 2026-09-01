package com.kunling.scheduling.app.catalog;

import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionSceneCatalogHttpTest {

    private ActionSceneCatalogRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(ActionSceneCatalogRepository.class);
        ActionSceneCatalogService service = new ActionSceneCatalogService(repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActionSceneCatalogController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Collections.emptyList()))
                .build();
    }

    @Test
    void 小写场景编码会规范化后查询操作() throws Exception {
        when(repository.countEnabledBusinessScene("PICK")).thenReturn(1);
        when(repository.selectEnabledOperations("PICK")).thenReturn(Arrays.asList(
                item("PICK", "MOVE_TO_POSE", "移动到位姿", 10),
                item("PICK", "GRIP.OPEN", "夹爪打开", 20)));

        mockMvc.perform(get("/api/action-business-scenes/pick/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data[0].code").value("MOVE_TO_POSE"))
                .andExpect(jsonPath("$.data[1].code").value("GRIP.OPEN"));

        verify(repository).selectEnabledOperations("PICK");
    }

    @Test
    void 非法场景编码返回四百且不访问数据库() throws Exception {
        mockMvc.perform(get("/api/action-business-scenes/bad.scene/operations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("业务场景编码格式不合法。"));

        verify(repository, never()).countEnabledBusinessScene(org.mockito.ArgumentMatchers.anyString());
        verify(repository, never()).selectEnabledOperations(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 未知或停用场景返回四百零四() throws Exception {
        when(repository.countEnabledBusinessScene("UNKNOWN")).thenReturn(0);

        mockMvc.perform(get("/api/action-business-scenes/unknown/operations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("业务场景不存在或未启用：UNKNOWN"));

        verify(repository, never()).selectEnabledOperations(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 启用场景没有操作时返回空数组() throws Exception {
        when(repository.countEnabledBusinessScene("HOME")).thenReturn(1);
        when(repository.selectEnabledOperations("HOME")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/action-business-scenes/HOME/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private ActionSceneCatalogItem item(String sceneCode,
                                        String itemCode,
                                        String displayName,
                                        int sortOrder) {
        return new ActionSceneCatalogItem(
                "OPERATION", sceneCode, itemCode, displayName, sortOrder, true);
    }
}

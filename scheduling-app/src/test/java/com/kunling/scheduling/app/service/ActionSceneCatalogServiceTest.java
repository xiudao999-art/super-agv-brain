package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.mapper.ActionSceneCatalogRepository;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionSceneCatalogServiceTest {

    @Test
    void 场景编码会去空格并转换为大写() {
        ActionSceneCatalogRepository repository = mock(ActionSceneCatalogRepository.class);
        when(repository.countEnabledBusinessScene("PICK")).thenReturn(1);
        when(repository.selectEnabledOperations("PICK")).thenReturn(Collections.emptyList());

        new ActionSceneCatalogService(repository).listOperations("  pick  ");

        verify(repository).countEnabledBusinessScene("PICK");
        verify(repository).selectEnabledOperations("PICK");
    }

    @Test
    void 空场景编码被拒绝() {
        ActionSceneCatalogRepository repository = mock(ActionSceneCatalogRepository.class);
        ActionSceneCatalogService service = new ActionSceneCatalogService(repository);

        assertThrows(InvalidRequestException.class, () -> service.listOperations("   "));
    }
}

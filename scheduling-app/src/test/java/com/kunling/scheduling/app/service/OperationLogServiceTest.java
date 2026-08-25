package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.OperationLogBatchDeleteResult;
import com.kunling.scheduling.app.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    @Test
    void 批量删除会去重并返回实际删除数量() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        when(mapper.deleteByIds(anyList())).thenReturn(1);
        OperationLogService service = new OperationLogService(mapper);

        OperationLogBatchDeleteResult result = service.deleteBatch(Arrays.asList(7L, 7L, 9L));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).deleteByIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(7L, 9L);
        assertThat(result.getRequestedCount()).isEqualTo(2);
        assertThat(result.getDeletedCount()).isEqualTo(1);
    }

    @Test
    void 批量删除拒绝空列表和非法日志标识() {
        OperationLogService service = new OperationLogService(mock(OperationLogMapper.class));

        assertThatThrownBy(() -> service.deleteBatch(Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少选择一条");
        assertThatThrownBy(() -> service.deleteBatch(Arrays.asList(1L, 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正整数");
    }
}

package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.OperationLogBatchDeleteResult;
import com.kunling.scheduling.app.domain.OperationLogConstraints;
import com.kunling.scheduling.app.domain.OperationLogPage;
import com.kunling.scheduling.app.domain.OperationLogQueryCriteria;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.app.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 操作日志查询与清理服务。 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Transactional(readOnly = true)
    public OperationLogPage page(OperationLogQueryCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("日志查询条件不能为空");
        }
        long total = operationLogMapper.count(criteria);
        List<SystemOperationLog> records = total == 0L
                ? Collections.emptyList()
                : operationLogMapper.selectPage(criteria, criteria.offset());
        return new OperationLogPage(total, criteria.getPageNum(), criteria.getPageSize(), records);
    }

    @Transactional
    public OperationLogBatchDeleteResult deleteBatch(List<Long> ids) {
        List<Long> distinctIds = validateAndDistinct(ids);
        int deletedCount = operationLogMapper.deleteByIds(distinctIds);
        return new OperationLogBatchDeleteResult(distinctIds.size(), deletedCount);
    }

    private List<Long> validateAndDistinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("至少选择一条需要删除的日志");
        }
        if (ids.size() > OperationLogConstraints.MAX_BATCH_DELETE_SIZE) {
            throw new IllegalArgumentException("单次最多删除 "
                    + OperationLogConstraints.MAX_BATCH_DELETE_SIZE + " 条日志");
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("日志 ID 必须为正整数");
            }
            distinctIds.add(id);
        }
        return new ArrayList<>(distinctIds);
    }
}

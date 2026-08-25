package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.app.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 以独立事务落库，使业务事务回滚时仍可保留失败日志。 */
@Service
public class OperationLogWriter {

    private final OperationLogMapper operationLogMapper;

    public OperationLogWriter(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(SystemOperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}

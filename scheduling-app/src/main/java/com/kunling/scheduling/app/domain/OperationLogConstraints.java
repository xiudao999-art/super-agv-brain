package com.kunling.scheduling.app.domain;

/** 操作日志接口和应用服务共享的边界约束。 */
public final class OperationLogConstraints {

    public static final int MAX_BATCH_DELETE_SIZE = 200;

    private OperationLogConstraints() {
    }
}

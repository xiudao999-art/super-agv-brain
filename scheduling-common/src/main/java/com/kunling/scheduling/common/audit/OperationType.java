package com.kunling.scheduling.common.audit;

/**
 * 业务操作类型。
 *
 * <p>枚举值是稳定的接口和存储契约，新增类型时应保持已有值不变。</p>
 */
public enum OperationType {
    CREATE,
    UPDATE,
    DELETE,
    PUBLISH,
    EXECUTE,
    RECOVER,
    UPLOAD,
    OTHER
}

package com.kunling.scheduling.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Controller 通用基类，集中维护成功响应的 HTTP 状态和 Result 结构。
 *
 * <p>业务 Controller 只负责参数接收和应用服务调用，不重复拼装响应字段。</p>
 */
public abstract class BaseController {

    protected <T> ResponseEntity<ApiResult<T>> success(T data) {
        return result(HttpStatus.OK, data);
    }

    protected ResponseEntity<ApiResult<Void>> success() {
        return result(HttpStatus.OK, null);
    }

    protected <T> ResponseEntity<ApiResult<T>> created(T data) {
        return result(HttpStatus.CREATED, data);
    }

    private <T> ResponseEntity<ApiResult<T>> result(HttpStatus status, T data) {
        return ResponseEntity.status(status).body(ApiResult.success(status.value(), data));
    }
}

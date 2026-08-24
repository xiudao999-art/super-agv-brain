package com.kunling.scheduling.common.web;

/**
 * Controller 通用基类，集中维护成功响应的 Result 结构。
 *
 * <p>业务 Controller 只负责参数接收和应用服务调用，不重复拼装响应字段。</p>
 */
public abstract class BaseController {

    protected <T> ApiResult<T> success(T data) {
        return ApiResult.success(data);
    }

    protected ApiResult<Void> success() {
        return ApiResult.success();
    }

    protected <T> ApiResult<T> created(T data) {
        return ApiResult.created(data);
    }

    protected <T> ApiResult<T> accepted(T data) {
        return ApiResult.accepted(data);
    }

}

package com.kunling.scheduling.common.web;

import java.util.Optional;

/**
 * 第三方框架异常到统一错误语义的扩展接口。
 *
 * <p>业务模块只在确实存在框架特有异常时实现该接口，不再新增 ControllerAdvice。</p>
 */
public interface ApiExceptionMapper {

    Optional<ApiExceptionMapping> map(Exception exception);
}

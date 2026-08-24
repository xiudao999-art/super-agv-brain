package com.kunling.scheduling.common.config;

import com.kunling.scheduling.common.web.ApiExceptionMapper;
import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调度系统公共 Web 能力的自动装配入口。
 *
 * <p>业务模块引入 scheduling-common 后即可复用统一 Result、Controller 基类和异常出口；
 * 框架特有异常通过 {@link ApiExceptionMapper} 扩展，不再增加模块私有的 ControllerAdvice。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler(
            ObjectProvider<ApiExceptionMapper> exceptionMappers) {
        List<ApiExceptionMapper> orderedMappers = exceptionMappers.orderedStream()
                .collect(Collectors.toList());
        return new GlobalExceptionHandler(orderedMappers);
    }
}

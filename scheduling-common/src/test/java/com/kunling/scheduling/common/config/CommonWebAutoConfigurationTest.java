package com.kunling.scheduling.common.config;

import com.kunling.scheduling.common.exception.ErrorType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.ApiExceptionMapping;
import com.kunling.scheduling.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CommonWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    @Test
    void Web应用自动装配唯一全局异常处理器() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void 业务模块可以通过扩展接口映射第三方异常() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(Collections.singletonList(exception ->
                Optional.of(new ApiExceptionMapping(ErrorType.BAD_REQUEST, "第三方参数错误", null))));

        ResponseEntity<ApiResult<Object>> response =
                handler.handleUnexpectedException(new RuntimeException("framework"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody().getMessage()).isEqualTo("第三方参数错误");
    }
}

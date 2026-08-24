package com.kunling.scheduling.workflow.config;

import com.kunling.scheduling.common.exception.ErrorType;
import com.kunling.scheduling.common.web.ApiExceptionMapper;
import com.kunling.scheduling.common.web.ApiExceptionMapping;
import org.flowable.common.engine.api.FlowableException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 将 Flowable 的输入类异常接入全系统统一错误响应，不再建立模块私有异常处理器。 */
@Component
public class FlowableApiExceptionMapper implements ApiExceptionMapper {

    @Override
    public Optional<ApiExceptionMapping> map(Exception exception) {
        if (!(exception instanceof FlowableException)) {
            return Optional.empty();
        }
        return Optional.of(new ApiExceptionMapping(
                ErrorType.BAD_REQUEST,
                exception.getMessage(),
                null
        ));
    }
}

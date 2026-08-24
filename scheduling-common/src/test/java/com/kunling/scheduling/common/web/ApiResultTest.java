package com.kunling.scheduling.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 所有成功工厂都使用固定成功码200() {
        assertThat(ApiResult.success("data").getCode()).isEqualTo(ApiResponseCode.SUCCESS.getCode());
        assertThat(ApiResult.success().getCode()).isEqualTo(ApiResponseCode.SUCCESS.getCode());
        assertThat(ApiResult.created("data").getCode()).isEqualTo(ApiResponseCode.SUCCESS.getCode());
        assertThat(ApiResult.accepted("data").getCode()).isEqualTo(ApiResponseCode.SUCCESS.getCode());
        assertThat(ApiResponseCode.SUCCESS.getCode()).isEqualTo(200);
    }

    @Test
    void 系统响应码集合保持固定且无重复() {
        assertThat(Arrays.stream(ApiResponseCode.values()).map(ApiResponseCode::getCode))
                .containsExactly(200, 400, 401, 403, 404, 405, 409, 413, 415, 500, 503)
                .doesNotHaveDuplicates();
    }

    @Test
    void ApiResult工厂禁止传入任意数字响应码() {
        assertThat(Arrays.stream(ApiResult.class.getDeclaredMethods())
                .filter(this::isFactoryMethod)
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .doesNotContain(int.class, Integer.class);
    }

    @Test
    void 无业务数据时仍序列化data字段() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(ApiResult.success()));

        assertThat(json.get("code").asInt()).isEqualTo(200);
        assertThat(json.get("message").asText()).isEqualTo("操作成功");
        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();
    }

    private boolean isFactoryMethod(Method method) {
        return method.getName().equals("success")
                || method.getName().equals("created")
                || method.getName().equals("accepted")
                || method.getName().equals("failure");
    }
}

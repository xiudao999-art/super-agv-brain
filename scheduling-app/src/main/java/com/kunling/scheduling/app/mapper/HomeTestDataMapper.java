package com.kunling.scheduling.app.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.app.domain.HomeOverviewTestData;
import com.kunling.scheduling.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 从 classpath JSON 映射运行总览测试数据。 */
@Component
public class HomeTestDataMapper {

    public static final String DEFAULT_RESOURCE_PATH = "mock/home-overview-test-data.json";

    private final ObjectMapper objectMapper;
    private final Resource dataResource;

    @Autowired
    public HomeTestDataMapper(ObjectMapper objectMapper) {
        this(objectMapper, new ClassPathResource(DEFAULT_RESOURCE_PATH));
    }

    public HomeTestDataMapper(ObjectMapper objectMapper, Resource dataResource) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 解析器不能为空");
        this.dataResource = Objects.requireNonNull(dataResource, "测试数据资源不能为空");
    }

    public HomeOverviewTestData load() {
        try (InputStream inputStream = dataResource.getInputStream()) {
            return objectMapper.readValue(inputStream, HomeOverviewTestData.class);
        } catch (IOException exception) {
            throw new ServiceUnavailableException(
                    "运行总览测试数据加载失败，请检查 " + DEFAULT_RESOURCE_PATH, exception);
        }
    }
}

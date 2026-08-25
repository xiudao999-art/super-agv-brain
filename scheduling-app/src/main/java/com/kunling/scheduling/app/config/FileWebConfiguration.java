package com.kunling.scheduling.app.config;

import com.kunling.scheduling.app.service.ImageStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 将本地图片存储目录映射为可访问的静态资源路径。 */
@Configuration(proxyBeanMethods = false)
public class FileWebConfiguration implements WebMvcConfigurer {

    private final ImageStorageService imageStorageService;

    public FileWebConfiguration(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ImageStorageService.PUBLIC_PATH + "**")
                .addResourceLocations(imageStorageService.resourceLocation())
                .setCachePeriod(3600);
    }
}

package com.kunling.scheduling.app.file;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

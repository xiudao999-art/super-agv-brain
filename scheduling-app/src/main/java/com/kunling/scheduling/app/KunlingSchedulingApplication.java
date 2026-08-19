package com.kunling.scheduling.app;

import com.kunling.scheduling.action.ActionModuleConfiguration;
import com.kunling.scheduling.agvflow.AgvFlowModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** 坤灵调度系统主应用入口，业务能力通过显式导入模块配置进行装配。 */
@SpringBootApplication
@Import({ActionModuleConfiguration.class, AgvFlowModuleConfiguration.class})
public class KunlingSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(KunlingSchedulingApplication.class, args);
    }
}

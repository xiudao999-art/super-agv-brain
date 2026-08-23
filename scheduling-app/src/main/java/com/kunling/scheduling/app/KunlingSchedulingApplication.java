package com.kunling.scheduling.app;

import com.kunling.scheduling.action.config.ActionModuleConfiguration;
import com.kunling.scheduling.agvflow.AgvFlowModuleConfiguration;
import com.kunling.scheduling.workflow.WorkflowModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** 坤灵调度系统主应用入口，业务能力通过显式导入模块配置进行装配。 */
@SpringBootApplication
@Import({ActionModuleConfiguration.class, AgvFlowModuleConfiguration.class, WorkflowModuleConfiguration.class})
public class KunlingSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(KunlingSchedulingApplication.class, args);
    }
}

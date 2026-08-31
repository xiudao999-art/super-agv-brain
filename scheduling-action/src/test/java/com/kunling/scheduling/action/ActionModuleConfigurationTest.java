package com.kunling.scheduling.action;

import com.kunling.scheduling.action.config.ActionModuleConfiguration;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.controller.ActionController;
import com.kunling.scheduling.action.controller.ActionErrorMappingController;
import com.kunling.scheduling.action.controller.ActionProtocolCatalogController;
import com.kunling.scheduling.action.controller.ExecutionController;
import com.kunling.scheduling.action.controller.RobotSessionController;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleService;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorMappingEngine;
import com.kunling.scheduling.action.exceptionmapping.application.ClientFaultCatalog;
import com.kunling.scheduling.action.execution.application.ActionExecutionEventProcessor;
import com.kunling.scheduling.action.execution.application.ActionExecutionReportMapper;
import com.kunling.scheduling.action.execution.application.ActionExecutionReportPublisher;
import com.kunling.scheduling.action.execution.application.ActionExecutionStore;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEventRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
import com.kunling.scheduling.action.execution.infrastructure.JpaActionExecutionStore;
import com.kunling.scheduling.action.robotbridge.config.Knife4jConfiguration;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ActionModuleConfigurationTest {

    @Test
    void componentScanCoversTheWholeActionModule() {
        ComponentScan scan = AnnotatedElementUtils.findMergedAnnotation(
                ActionModuleConfiguration.class, ComponentScan.class);
        assertThat(scan).isNotNull();
        assertThat(scan.basePackageClasses()).containsExactly(ActionModulePackage.class);
    }

    @Test
    void tcpDeploymentSettingsRemainExternallyConfigurable() {
        ConfigurationProperties binding = AnnotatedElementUtils.findMergedAnnotation(
                RobotBridgeProperties.class, ConfigurationProperties.class);
        assertThat(binding).isNotNull();
        assertThat(binding.prefix()).isEqualTo("kunling.action.robot-bridge");
    }

    @Test
    void jpaExecutionStoreUsesSpringConstructorInjection() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("actionExecutionRepository",
                mock(ActionExecutionRepository.class));
        context.getBeanFactory().registerSingleton("actionExecutionEventRepository",
                mock(ActionExecutionEventRepository.class));
        context.getBeanFactory().registerSingleton("jsonCodec", mock(JsonCodec.class));
        context.getBeanFactory().registerSingleton("transactionManager",
                mock(PlatformTransactionManager.class));
        context.register(JpaActionExecutionStore.class);
        try {
            assertThatCode(context::refresh).doesNotThrowAnyException();
        } finally {
            context.close();
        }
    }

    @Test
    void eventProcessorUsesTheMinimalReportMapperDependencies() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("actionExecutionStore", mock(ActionExecutionStore.class));
        context.getBeanFactory().registerSingleton("mappingRuleService",
                mock(ActionErrorMappingRuleService.class));
        context.register(BusinessErrorMappingEngine.class, ClientFaultCatalog.class,
                ActionExecutionReportMapper.class, ActionExecutionReportPublisher.class,
                ActionExecutionEventProcessor.class);
        try {
            assertThatCode(context::refresh).doesNotThrowAnyException();
        } finally {
            context.close();
        }
    }

    @Test
    void everyActionControllerEndpointHasChineseDocumentation() {
        OpenAPIDefinition definition = Knife4jConfiguration.class.getAnnotation(OpenAPIDefinition.class);
        assertThat(definition).isNotNull();
        Class<?>[] controllers = { ActionController.class, ActionErrorMappingController.class,
                ExecutionController.class, ActionProtocolCatalogController.class,
                RobotSessionController.class };
        for (Class<?> controller : controllers) {
            assertThat(controller.getAnnotation(Tag.class)).isNotNull();
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
                Operation operation = method.getAnnotation(Operation.class);
                assertThat(operation).as(controller.getSimpleName() + "." + method.getName()).isNotNull();
                assertThat(operation.summary()).matches(".*[\\u4e00-\\u9fa5].*");
            }
        }
    }

    @Test
    void actionDefinitionAndExecutionCommandExposeOnlyCurrentFields() {
        Schema<?> actionSchema = ModelConverters.getInstance().read(ActionDefinition.class)
                .get("ActionDefinition");
        assertThat(actionSchema.getProperties().keySet())
                .containsExactlyInAnyOrder("id", "name", "enabled", "timeoutMs", "steps");
        Schema<?> commandSchema = ModelConverters.getInstance().read(ExecuteActionCommand.class)
                .get("ExecuteActionCommand");
        assertThat(commandSchema.getProperties().keySet())
                .containsExactlyInAnyOrder("actionInstanceId", "actionDefinitionId", "robotId");
    }

    @Test
    void removedSchemaAndParameterSetTypesDoNotExist() {
        assertThatThrownBy(() -> Class.forName(
                "com.kunling.scheduling.action.definition.domain.ParameterSchema"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.kunling.scheduling.action.commissioning.application.ActionParameterSetService"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}

package com.kunling.scheduling.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionEventProcessor;
import com.kunling.scheduling.action.execution.application.ActionExecutionStore;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEventRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
import com.kunling.scheduling.action.execution.infrastructure.JpaActionExecutionStore;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.controller.ActionController;
import com.kunling.scheduling.action.controller.ActionProtocolCatalogController;
import com.kunling.scheduling.action.controller.ExecutionController;
import com.kunling.scheduling.action.controller.ParameterSetController;
import com.kunling.scheduling.action.controller.RobotSessionController;
import com.kunling.scheduling.action.robotbridge.config.Knife4jConfiguration;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import com.kunling.scheduling.action.shared.JsonCodec;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class ActionModuleConfigurationTest {

    @Test
    void businessDefaultsStayInModuleWhileTcpDeploymentSettingsAreExternallyConfigurable() {
        ConfigurationProperties robotBridgeBinding = AnnotatedElementUtils.findMergedAnnotation(
                RobotBridgeProperties.class,
                ConfigurationProperties.class
        );
        assertThat(robotBridgeBinding).isNotNull();
        assertThat(robotBridgeBinding.prefix()).isEqualTo("kunling.action.robot-bridge");

        EnableConfigurationProperties enabledProperties = AnnotatedElementUtils.findMergedAnnotation(
                ActionModuleConfiguration.class,
                EnableConfigurationProperties.class
        );
        assertThat(enabledProperties).isNotNull();
        assertThat(enabledProperties.value()).contains(RobotBridgeProperties.class);

        RobotBridgeProperties robotBridgeProperties = new RobotBridgeProperties(
                true, null, 8080, 0, 0, 0, null
        );
        assertThat(robotBridgeProperties.acceptedActionTypes())
                .containsExactly("MOVE", "ARM.PICK", "ARM.PLACE", "ARM.PICK_BATCH",
                        "ARM.PLACE_BATCH", "ARM.HOME", "VISION.CAPTURE");
    }

    @Test
    void jpaExecutionStoreCanBeCreatedBySpringConstructorInjection() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton(
                "actionExecutionRepository", mock(ActionExecutionRepository.class));
        context.getBeanFactory().registerSingleton(
                "actionExecutionEventRepository", mock(ActionExecutionEventRepository.class));
        context.getBeanFactory().registerSingleton("jsonCodec", mock(JsonCodec.class));
        context.getBeanFactory().registerSingleton(
                "transactionManager", mock(PlatformTransactionManager.class));
        context.register(JpaActionExecutionStore.class);

        try {
            // 直接走生产组件的 Spring 构造器选择逻辑，防止多构造器退回到不存在的无参构造器。
            assertThatCode(context::refresh).doesNotThrowAnyException();
        } finally {
            context.close();
        }
    }

    @Test
    void executionEventProcessorCanBeCreatedBySpringConstructorInjection() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("actionExecutionStore", mock(ActionExecutionStore.class));
        context.register(ActionExecutionEventProcessor.class);

        try {
            assertThatCode(context::refresh).doesNotThrowAnyException();
        } finally {
            context.close();
        }
    }

    @Test
    void knife4jDocumentationStaysChineseAndFollowsEveryActionEndpoint() {
        OpenAPIDefinition definition = Knife4jConfiguration.class.getAnnotation(OpenAPIDefinition.class);
        assertThat(definition).isNotNull();
        assertThat(definition.info().title()).contains("坤灵", "Action");

        Class<?>[] controllers = {
                ActionController.class,
                ParameterSetController.class,
                ExecutionController.class,
                ActionProtocolCatalogController.class,
                RobotSessionController.class
        };
        int documentedEndpointCount = 0;
        for (Class<?> controller : controllers) {
            Tag tag = controller.getAnnotation(Tag.class);
            assertThat(tag).as(controller.getSimpleName() + " 缺少 Knife4j 中文分组").isNotNull();
            assertThat(tag.name()).matches(".*[\\u4e00-\\u9fa5].*");

            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                Operation operation = method.getAnnotation(Operation.class);
                assertThat(operation)
                        .as(controller.getSimpleName() + "." + method.getName() + " 缺少接口说明")
                        .isNotNull();
                assertThat(operation.summary()).matches(".*[\\u4e00-\\u9fa5].*");
                documentedEndpointCount++;
            }
        }
        assertThat(documentedEndpointCount).isEqualTo(19);

        Schema<?> actionDefinitionSchema = ModelConverters.getInstance()
                .read(ActionDefinition.class)
                .get("ActionDefinition");
        assertThat(actionDefinitionSchema).isNotNull();
        assertThat(actionDefinitionSchema.getDescription()).isEqualTo("当前 Action 的动态配置定义");
        assertThat(actionDefinitionSchema.getProperties().get("actionKey").getDescription())
                .isEqualTo("Action 唯一标识");
    }
}

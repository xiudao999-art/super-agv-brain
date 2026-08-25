package com.kunling.scheduling.app;

import com.kunling.scheduling.common.audit.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 固化重要业务日志白名单，防止普通 CRUD 被无意纳入后造成日志量膨胀。 */
class ImportantOperationLogPolicyTest {

    private static final Set<String> APPROVED_HANDLERS = new LinkedHashSet<>(Arrays.asList(
            "com.kunling.scheduling.action.controller.ActionController#activate",
            "com.kunling.scheduling.action.controller.ActionController#disable",
            "com.kunling.scheduling.action.controller.ActionController#delete",
            "com.kunling.scheduling.action.controller.ActionErrorMappingController#activate",
            "com.kunling.scheduling.action.controller.ActionErrorMappingController#disable",
            "com.kunling.scheduling.action.controller.ActionErrorMappingController#delete",
            "com.kunling.scheduling.action.controller.ExecutionController#start",
            "com.kunling.scheduling.action.controller.ExecutionController#query",
            "com.kunling.scheduling.app.controller.LabController#initialize",
            "com.kunling.scheduling.app.controller.LabConfigController#deleteDraft",
            "com.kunling.scheduling.app.controller.LabConfigController#publish",
            "com.kunling.scheduling.app.controller.OperationLogController#deleteBatch",
            "com.kunling.scheduling.workflow.controller.OrderController#sync",
            "com.kunling.scheduling.workflow.controller.WorkflowController#deploy",
            "com.kunling.scheduling.workflow.controller.WorkflowController#start",
            "com.kunling.scheduling.workflow.controller.WorkflowController#suspend",
            "com.kunling.scheduling.workflow.controller.WorkflowController#activate",
            "com.kunling.scheduling.workflow.controller.WorkflowController#terminate",
            "com.kunling.scheduling.workflow.controller.WorkflowController#claim",
            "com.kunling.scheduling.workflow.controller.WorkflowController#complete",
            "com.kunling.scheduling.workflow.controller.WorkflowTemplateController#deploy",
            "com.kunling.scheduling.workflow.controller.WorkflowTemplateController#delete",
            "com.kunling.scheduling.workflow.controller.WorkflowTemplateController#start"
    ));

    @Test
    void 仅批准的重要业务接口启用日志() throws Exception {
        Map<String, OperationLog> operationLogs = scanOperationLogs();

        assertThat(operationLogs.keySet())
                .containsExactlyInAnyOrderElementsOf(APPROVED_HANDLERS);
    }

    @Test
    void 白名单接口均不记录响应体且大报文部署不记录请求() throws Exception {
        Map<String, OperationLog> operationLogs = scanOperationLogs();

        operationLogs.forEach((handler, operationLog) ->
                assertThat(operationLog.recordResponse())
                        .as("%s 不应记录响应体", handler)
                        .isFalse());
        OperationLog deploymentLog = operationLogs.get(
                "com.kunling.scheduling.workflow.controller.WorkflowController#deploy");
        assertThat(deploymentLog).isNotNull();
        assertThat(deploymentLog.recordRequest()).isFalse();
    }

    private Map<String, OperationLog> scanOperationLogs() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Map<String, OperationLog> operationLogs = new LinkedHashMap<>();
        scanner.findCandidateComponents("com.kunling.scheduling").forEach(candidate -> {
            try {
                Class<?> controllerType = Class.forName(candidate.getBeanClassName());
                for (Method method : controllerType.getDeclaredMethods()) {
                    OperationLog operationLog = method.getAnnotation(OperationLog.class);
                    if (operationLog != null) {
                        operationLogs.put(controllerType.getName() + "#" + method.getName(), operationLog);
                    }
                }
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("无法加载 Controller: " + candidate.getBeanClassName(), exception);
            }
        });
        return operationLogs;
    }
}

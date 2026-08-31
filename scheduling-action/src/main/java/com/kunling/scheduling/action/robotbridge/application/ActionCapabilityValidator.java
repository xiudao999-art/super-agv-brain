package com.kunling.scheduling.action.robotbridge.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureRule;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import org.springframework.stereotype.Component;

/** 在启用和下发前验证 Action 与机器人当前会话的原子能力契约。 */
@Component
public class ActionCapabilityValidator {

    public void validate(ActionDefinition definition, RobotSessionView session) {
        if (definition == null) throw new IllegalArgumentException("Action 定义不能为空。");
        if (session == null) throw new IllegalArgumentException("机器人会话不能为空。");
        for (ActionStepDefinition step : definition.steps()) {
            requireOperationSupportingTimeout(session, step.operation(), definition.timeoutMs());
            for (ActionFailureRule rule : step.onFailure().rules()) {
                validateDirective(rule.directive(), session, definition.timeoutMs());
            }
            validateDirective(step.onFailure().defaultDirective(), session, definition.timeoutMs());
        }
    }

    private void validateDirective(ActionFailureDirective directive,
                                   RobotSessionView session,
                                   int timeoutMs) {
        String feature = directive.action().name();
        if (!session.policyFeatures().contains(feature)) {
            throw new RobotUnavailableException("机器人当前会话不支持失败策略：" + feature);
        }
        if (directive.verifyOperation() != null) {
            requireOperationSupportingTimeout(session, directive.verifyOperation(), timeoutMs);
        }
        if (directive.onExhaust() != null
                && !session.policyFeatures().contains(directive.onExhaust().name())) {
            throw new RobotUnavailableException("机器人当前会话不支持耗尽策略："
                    + directive.onExhaust().name());
        }
    }

    private RobotOperationCapability requireOperation(RobotSessionView session, String operation) {
        RobotOperationCapability capability = session.operationCapabilities().get(operation);
        if (capability == null) {
            throw new RobotUnavailableException("机器人当前会话未注册原子操作：" + operation);
        }
        return capability;
    }

    private void requireOperationSupportingTimeout(RobotSessionView session,
                                                   String operation,
                                                   int timeoutMs) {
        RobotOperationCapability capability = requireOperation(session, operation);
        if (timeoutMs < capability.minTimeoutMs() || timeoutMs > capability.maxTimeoutMs()) {
            throw new RobotUnavailableException("Action timeoutMs=" + timeoutMs
                    + " 超出操作 " + operation + " 支持范围 "
                    + capability.minTimeoutMs() + "-" + capability.maxTimeoutMs());
        }
    }
}

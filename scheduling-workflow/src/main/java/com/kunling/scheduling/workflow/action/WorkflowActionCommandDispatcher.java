package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在流程业务事务提交后向 Action 模块发送命令。
 *
 * <p>这个时序确保下游即时回报时，已经可以通过 actionInstanceId 查到已提交的
 * 流程节点，同时避免 Action 的准备事务加入流程外层事务。</p>
 */
@Service
public class WorkflowActionCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActionCommandDispatcher.class);

    private final WorkFlowExecutionsGateway executionsGateway;
    private final WorkflowActionDispatchFailureHandler failureHandler;

    public WorkflowActionCommandDispatcher(WorkFlowExecutionsGateway executionsGateway,
                                           WorkflowActionDispatchFailureHandler failureHandler) {
        this.executionsGateway = executionsGateway;
        this.failureHandler = failureHandler;
    }

    public void dispatchAfterCommit(ExecuteActionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Action 执行命令不能为空");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(command);
                }
            });
            return;
        }
        dispatch(command);
    }

    private void dispatch(ExecuteActionCommand command) {
        try {
            executionsGateway.execute(command);
            log.info("Action 命令已提交，actionInstanceId={}, actionDefinitionId={}, robotId={}",
                    command.actionInstanceId(), command.actionDefinitionId(), command.robotId());
        } catch (RuntimeException exception) {
            try {
                failureHandler.handle(command.actionInstanceId(), exception);
            } catch (RuntimeException handlingException) {
                // afterCommit 发生时流程事务已经提交，不能再向调用方制造“已回滚”的假象。
                log.error("Action 下发失败且人工恢复状态保存失败，actionInstanceId={}",
                        command.actionInstanceId(), handlingException);
            }
        }
    }
}

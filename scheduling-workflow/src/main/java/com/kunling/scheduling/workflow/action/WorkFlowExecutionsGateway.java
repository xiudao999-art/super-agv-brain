package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionGateway;
import com.kunling.scheduling.action.execution.application.ActionExecutionReceipt;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * AGV Flow 调用 Action 模块的唯一入口。
 *
 * <p>流程引擎只能提交三字段执行命令，不依赖 Action 的预览、仓储和设备通信实现。</p>
 */
@Service
public class WorkFlowExecutionsGateway {

    private final ObjectProvider<ActionExecutionGateway> actionExecutionGatewayProvider;

    public WorkFlowExecutionsGateway(ObjectProvider<ActionExecutionGateway> actionExecutionGatewayProvider) {
        this.actionExecutionGatewayProvider = actionExecutionGatewayProvider;
    }

    /** 根据 Action 定义标识发起一次幂等执行。 */
    public ActionExecutionReceipt execute(ExecuteActionCommand command) {
        return actionExecutionGatewayProvider.getObject().execute(command);
    }
}

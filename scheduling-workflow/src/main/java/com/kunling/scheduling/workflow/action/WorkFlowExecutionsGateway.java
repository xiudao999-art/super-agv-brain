package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionReceipt;
import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ActionPackagePreview;
import com.kunling.scheduling.action.execution.application.ActionPackagePreviewRequest;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AGV Flow 调用 Action 模块的唯一入口。
 *
 * <p>流程业务只依赖本门面，不直接访问 Action 模块的仓储、TCP 实现或控制器。</p>
 */
@Service
public class WorkFlowExecutionsGateway {

    private final ObjectProvider<ActionExecutionService> actionExecutionServiceProvider;


    public WorkFlowExecutionsGateway(
            ObjectProvider<ActionExecutionService> actionExecutionServiceProvider) {
        this.actionExecutionServiceProvider = actionExecutionServiceProvider;
    }



    /** 预览当前 Action 定义编译出的完整动作包。 */
    public ActionPackagePreview preview(ActionPackagePreviewRequest request) {
        return actionExecutionServiceProvider.getObject().preview(request);
    }

    /** 根据 Action 定义标识发起一次幂等执行，不再回传或校验预览哈希。 */
    public ActionExecutionReceipt execute(ExecuteActionCommand request) {
        return actionExecutionServiceProvider.getObject().execute(request);
    }

    public ActionExecutionView get(String actionInstanceId) {
        return actionExecutionServiceProvider.getObject().get(actionInstanceId);
    }

    public List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit) {
        return actionExecutionServiceProvider.getObject().getEvents(actionInstanceId, limit);
    }

}

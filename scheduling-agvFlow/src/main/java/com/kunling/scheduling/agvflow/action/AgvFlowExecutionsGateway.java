package com.kunling.scheduling.agvflow.action;

import com.kunling.scheduling.action.execution.application.*;
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
public class AgvFlowExecutionsGateway {

    private final ObjectProvider<ActionExecutionService> actionExecutionServiceProvider;


    public AgvFlowExecutionsGateway(
            ObjectProvider<ActionExecutionService> actionExecutionServiceProvider) {
        this.actionExecutionServiceProvider = actionExecutionServiceProvider;
    }



    /**
     * 预览最终动作包；正式执行前必须先调用本方法取得 packageHash。
     */
    public ActionPackagePreview preview(StartActionExecutionRequest request) {
        return actionExecutionServiceProvider.getObject().preview(request);
    }

    /**
     * 下发已预览并确认 packageHash 的动作。
     */
    public ActionExecutionReceipt execute(ExecuteActionCommand request) {
        return actionExecutionServiceProvider.getObject().execute(request);
    }

    public ActionExecutionView get(String actionInstanceId) {
        return actionExecutionServiceProvider.getObject().get(actionInstanceId);
    }

    public List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit) {
        return actionExecutionServiceProvider.getObject().getEvents(actionInstanceId, limit);
    }

    /**
     * 主动向机器人查询动作状态，并返回当前持久化状态。
     */
    public ActionExecutionView query(String actionInstanceId) {
        return actionExecutionServiceProvider.getObject().query(actionInstanceId);
    }
}

package com.kunling.scheduling.action.execution.application;

/**
 * 执行引擎调用 Action 模块的本地接口。
 *
 * <p>执行引擎只提交一次业务命令；Action 内部负责预览、哈希校验、快照冻结和完整包下发。
 * 本方法只返回提交回执，最终成功或失败仍通过 {@link ActionExecutionReportSink} 交付。</p>
 */
public interface ActionExecutionGateway {

    ActionExecutionReceipt execute(ExecuteActionCommand command);
}

package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;

/**
 * Action 模块向执行引擎交付最终结果的本地 seam。
 *
 * <p>执行引擎实现本接口后会自动被 Spring 注入；没有实现时 Action 模块仍可独立运行。
 * 一个 actionInstanceId 只交付一次，接收方仍应按 actionInstanceId 幂等。</p>
 */
@FunctionalInterface
public interface ActionExecutionReportSink {
    void accept(ActionExecutionReport report);
}

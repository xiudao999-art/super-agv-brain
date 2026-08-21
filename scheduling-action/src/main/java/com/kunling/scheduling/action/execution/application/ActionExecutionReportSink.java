package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;

/**
 * Action 模块向执行引擎交付执行事实的本地 seam。
 *
 * <p>执行引擎实现本接口后会自动被 Spring 注入；没有实现时 Action 模块仍可独立运行。
 * 接收方必须按 eventId 幂等，并且只能使用 terminal=true 的报告驱动流程跳转。</p>
 */
@FunctionalInterface
public interface ActionExecutionReportSink {
    void accept(ActionExecutionReport report);
}

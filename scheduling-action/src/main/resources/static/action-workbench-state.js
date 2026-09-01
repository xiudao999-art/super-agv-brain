(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionWorkbenchState = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function create(savedTask) {
    return {
      actionDefinitionId: savedTask && savedTask.actionDefinitionId || null,
      executionId: savedTask && savedTask.executionId || null,
      executionLocked: Boolean(savedTask && savedTask.executionLocked),
      serverLocked: false
    };
  }

  function lockForExecution(state, actionDefinitionId, executionId) {
    state.actionDefinitionId = actionDefinitionId;
    state.executionId = executionId;
    state.executionLocked = true;
    return state;
  }

  /** UNKNOWN_HOLD 也是 Action 终态；它解除定义锁，但流程仍需人工处置。 */
  function releaseAfterTerminal(state, execution) {
    if (!state || !execution) return false;
    if (!["FINISHED", "REJECTED", "FAILED", "UNKNOWN_HOLD"].includes(execution.state)) return false;
    state.executionLocked = false;
    state.executionId = execution.actionInstanceId || state.executionId;
    return true;
  }

  /** 编辑 Action 不依赖机器人在线；机器人只参与启用、预览和执行能力校验。 */
  function canEdit(state) {
    return Boolean(state) && !state.executionLocked && !state.serverLocked;
  }

  return { create, lockForExecution, releaseAfterTerminal, canEdit };
});

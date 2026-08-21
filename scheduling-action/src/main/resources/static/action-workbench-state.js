(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionWorkbenchState = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function create(savedTask) {
    return {
      actionKey: savedTask && savedTask.actionKey || null,
      executionId: savedTask && savedTask.executionId || null,
      executionLocked: Boolean(savedTask && savedTask.executionLocked),
      serverLocked: false
    };
  }

  function lockForExecution(state, actionKey, executionId) {
    state.actionKey = actionKey;
    state.executionId = executionId;
    state.executionLocked = true;
    return state;
  }

  /**
   * 物理结果已经明确的终态允许继续调参；结果未知时必须继续冻结，避免重复下发物理动作。
   * PHYSICAL_DONE 是当前后端完成态，COMPLETED 作为协议兼容别名保留。
   */
  function releaseAfterSettled(state, execution) {
    if (!state || !execution || execution.physicalResultKnown !== true) return false;
    const terminalStates = ["PHYSICAL_DONE", "COMPLETED", "REJECTED", "FAILED", "CANCELLED"];
    if (!terminalStates.includes(execution.state)) return false;
    state.executionLocked = false;
    state.executionId = execution.actionInstanceId || state.executionId;
    return true;
  }

  function newTask() {
    return create(null);
  }

  function canEdit(state, actionKey) {
    return !state.executionLocked && !state.serverLocked;
  }

  return { create, lockForExecution, releaseAfterSettled, newTask, canEdit };
});

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
      acknowledgedUnknownHoldId: savedTask && savedTask.acknowledgedUnknownHoldId || null,
      serverLocked: false
    };
  }

  function lockForExecution(state, actionKey, executionId) {
    state.actionKey = actionKey;
    state.executionId = executionId;
    state.executionLocked = true;
    state.acknowledgedUnknownHoldId = null;
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
    state.acknowledgedUnknownHoldId = null;
    return true;
  }

  /**
   * 开发联调时允许人工放弃 UNKNOWN_HOLD 对页面造成的编辑锁。
   * 该操作只改变浏览器工作台状态，不会伪造物理结果，也不会修改服务端执行记录。
   */
  function canReleaseUnknownHoldForCommissioning(state, execution) {
    if (!state || !execution) return false;
    if (execution.state !== "UNKNOWN_HOLD" || execution.physicalResultKnown === true) return false;
    if (!execution.actionInstanceId) return false;
    return state.acknowledgedUnknownHoldId !== execution.actionInstanceId;
  }

  function releaseUnknownHoldForCommissioning(state, execution) {
    if (!canReleaseUnknownHoldForCommissioning(state, execution)) return false;
    state.executionId = execution.actionInstanceId;
    state.executionLocked = false;
    state.acknowledgedUnknownHoldId = execution.actionInstanceId;
    return true;
  }

  function newTask() {
    return create(null);
  }

  function canEdit(state, actionKey) {
    return !state.executionLocked && !state.serverLocked;
  }

  return {
    create,
    lockForExecution,
    releaseAfterSettled,
    canReleaseUnknownHoldForCommissioning,
    releaseUnknownHoldForCommissioning,
    newTask,
    canEdit
  };
});

const test = require("node:test");
const assert = require("node:assert/strict");
const stateApi = require("../../main/resources/static/action-workbench-state.js");

test("执行期间当前联调任务保持只读", () => {
  const state = stateApi.create();
  stateApi.lockForExecution(state, "ARM.CUSTOM.PICK", "execution-1");

  assert.equal(stateApi.canEdit(state, "ARM.CUSTOM.PICK"), false);
  assert.equal(stateApi.releaseAfterSettled(state, {
    state: "RUNNING", physicalResultKnown: false, actionInstanceId: "execution-1"
  }), false);
  assert.equal(stateApi.canEdit(state, "ARM.CUSTOM.PICK"), false);
});

test("联调成功后自动解除冻结并保留执行编号", () => {
  const state = stateApi.lockForExecution(stateApi.create(), "MOVE.CUSTOM", "execution-2");

  assert.equal(stateApi.releaseAfterSettled(state, {
    state: "PHYSICAL_DONE", physicalResultKnown: true, actionInstanceId: "execution-2"
  }), true);
  assert.equal(stateApi.canEdit(state, "MOVE.CUSTOM"), true);
  assert.equal(state.executionId, "execution-2");
});

test("物理结果明确的执行失败后自动解除冻结", () => {
  const state = stateApi.lockForExecution(stateApi.create(), "MOVE.CUSTOM", "execution-3");

  assert.equal(stateApi.releaseAfterSettled(state, {
    state: "FAILED", physicalResultKnown: true, actionInstanceId: "execution-3"
  }), true);
  assert.equal(stateApi.canEdit(state, "MOVE.CUSTOM"), true);
});

test("机器人忙导致动作未开始时自动解除冻结", () => {
  const state = stateApi.lockForExecution(stateApi.create(), "MOVE.CUSTOM", "execution-busy");

  assert.equal(stateApi.releaseAfterSettled(state, {
    state: "REJECTED", physicalResultKnown: true, actionInstanceId: "execution-busy"
  }), true);
  assert.equal(stateApi.canEdit(state, "MOVE.CUSTOM"), true);
});

test("UNKNOWN_HOLD 或物理结果未知时继续冻结", () => {
  const holdState = stateApi.lockForExecution(stateApi.create(), "MOVE.CUSTOM", "execution-4");
  const failedUnknownState = stateApi.lockForExecution(stateApi.create(), "MOVE.CUSTOM", "execution-5");

  assert.equal(stateApi.releaseAfterSettled(holdState, {
    state: "UNKNOWN_HOLD", physicalResultKnown: false, actionInstanceId: "execution-4"
  }), false);
  assert.equal(stateApi.releaseAfterSettled(failedUnknownState, {
    state: "FAILED", physicalResultKnown: false, actionInstanceId: "execution-5"
  }), false);
  assert.equal(stateApi.canEdit(holdState, "MOVE.CUSTOM"), false);
  assert.equal(stateApi.canEdit(failedUnknownState, "MOVE.CUSTOM"), false);
});

test("开发联调可人工放弃 UNKNOWN_HOLD 的页面锁，但不能解锁运行中动作", () => {
  const holdState = stateApi.lockForExecution(stateApi.create(), "ARM.PICK", "execution-hold");
  const runningState = stateApi.lockForExecution(stateApi.create(), "ARM.PICK", "execution-running");
  const mismatchedState = stateApi.lockForExecution(stateApi.create(), "ARM.PICK", "execution-other");

  assert.equal(stateApi.releaseUnknownHoldForCommissioning(holdState, {
    state: "UNKNOWN_HOLD", physicalResultKnown: false, actionInstanceId: "execution-hold"
  }), true);
  assert.equal(stateApi.canEdit(holdState, "ARM.PICK"), true);
  assert.equal(holdState.executionId, "execution-hold");

  assert.equal(stateApi.releaseUnknownHoldForCommissioning(runningState, {
    state: "RUNNING", physicalResultKnown: false, actionInstanceId: "execution-running"
  }), false);
  assert.equal(stateApi.canEdit(runningState, "ARM.PICK"), false);

  assert.equal(stateApi.releaseUnknownHoldForCommissioning(mismatchedState, {
    state: "UNKNOWN_HOLD", physicalResultKnown: false, actionInstanceId: "execution-hold"
  }), true);
  assert.equal(mismatchedState.executionId, "execution-hold");
  assert.equal(stateApi.canEdit(mismatchedState, "ARM.PICK"), true);
});

test("页面锁已丢失但仍展示 UNKNOWN_HOLD 记录时也提供一次调试确认", () => {
  const restoredState = stateApi.create({
    actionKey: "ARM.PICK",
    executionId: "execution-restored",
    executionLocked: false
  });
  const execution = {
    state: "UNKNOWN_HOLD",
    physicalResultKnown: false,
    actionInstanceId: "execution-restored"
  };

  assert.equal(stateApi.canReleaseUnknownHoldForCommissioning(restoredState, execution), true);
  assert.equal(stateApi.releaseUnknownHoldForCommissioning(restoredState, execution), true);
  assert.equal(stateApi.canReleaseUnknownHoldForCommissioning(restoredState, execution), false);
  assert.equal(stateApi.canEdit(restoredState, "ARM.PICK"), true);
});

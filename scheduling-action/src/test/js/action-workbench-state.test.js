const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const stateApi = require("../../main/resources/static/action-workbench-state.js");

test("执行期间 Action 定义保持只读", () => {
  const state = stateApi.lockForExecution(stateApi.create(), "definition-1", "execution-1");
  assert.equal(stateApi.releaseAfterTerminal(state, {
    state: "RUNNING", physicalOutcome: "UNKNOWN", actionInstanceId: "execution-1"
  }), false);
  assert.equal(stateApi.canEdit(state, true), false);
});

test("所有 Action 终态都解除定义编辑锁", () => {
  for (const execution of [
    { state: "FINISHED", physicalOutcome: "CONFIRMED_SUCCEEDED", actionInstanceId: "execution-2" },
    { state: "FAILED", physicalOutcome: "CONFIRMED_FAILED", actionInstanceId: "execution-2" },
    { state: "REJECTED", physicalOutcome: "NOT_STARTED", actionInstanceId: "execution-2" },
    { state: "UNKNOWN_HOLD", physicalOutcome: "UNKNOWN", actionInstanceId: "execution-2" }
  ]) {
    const state = stateApi.lockForExecution(stateApi.create(), "definition-1", "execution-2");
    assert.equal(stateApi.releaseAfterTerminal(state, execution), true);
    assert.equal(stateApi.canEdit(state, true), true);
  }
});

test("机器人离线时禁止编辑", () => {
  assert.equal(stateApi.canEdit(stateApi.create(), false), false);
});

test("工作台不再引用 Schema 参数集 revision 和旧执行字段", () => {
  const staticRoot = path.resolve(__dirname, "../../main/resources/static");
  const source = ["index.html", "app.js", "action-workbench-state.js"]
    .map(file => fs.readFileSync(path.join(staticRoot, file), "utf8")).join("\n");
  for (const removed of ["parameterSet", "schemaHash", "expectedRevision", "actionKey",
    "workflowInstanceId", "workflowNodeInstanceId", "configSnapshot"]) {
    assert.equal(source.includes(removed), false, `仍引用旧字段 ${removed}`);
  }
});

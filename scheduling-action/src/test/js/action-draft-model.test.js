const test = require("node:test");
const assert = require("node:assert/strict");
const model = require("../../main/resources/static/action-draft-model.js");

function definition(name) {
  return { id: null, name, enabled: false, timeoutMs: 60000, steps: [{
    stepId: "step-01", operation: "CUSTOM.OPERATION", gate: true,
    params: { text: "1", number: 1, flag: false, nothing: null, nested: [1, { value: true }] },
    onFailure: { rules: [], defaultDirective: { action: "STOP_AND_REPORT", maxRetries: 0, delayMs: 0 } }
  }] };
}

test("完整 JSON 成功应用时整体覆盖并保留所有类型", () => {
  const oldDraft = definition("旧草稿");
  const incoming = definition("新 JSON");
  incoming.steps[0].params.number = 2.5;
  const applied = model.applyJson(JSON.stringify(incoming), null);

  assert.equal(applied.name, "新 JSON");
  assert.deepEqual(applied.steps[0].params, incoming.steps[0].params);
  assert.equal(oldDraft.name, "旧草稿");
});

test("JSON 解析或结构失败时不修改原草稿", () => {
  const oldDraft = definition("保留");
  const snapshot = JSON.stringify(oldDraft);

  assert.throws(() => model.applyJson("{", null), /合法/);
  assert.throws(() => model.applyJson(JSON.stringify({ ...oldDraft, steps: {} }), null), /steps/);
  assert.equal(JSON.stringify(oldDraft), snapshot);
});

test("保存快照不执行 Action 内容和 commandId 校验", () => {
  const incompleteDraft = {
    id: null,
    name: "",
    enabled: false,
    timeoutMs: -1,
    steps: [{
      stepId: "",
      operation: "custom-operation",
      params: { commandId: "invalid" },
      gate: true,
      onFailure: null
    }]
  };

  const snapshot = model.snapshotForSave(incompleteDraft);

  assert.deepEqual(snapshot, incompleteDraft);
  assert.notEqual(snapshot, incompleteDraft);
});

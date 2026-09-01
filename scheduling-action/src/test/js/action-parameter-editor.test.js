const test = require("node:test");
const assert = require("node:assert/strict");
const editor = require("../../main/resources/static/action-parameter-editor.js");

test("递归参数操作保留 JSON 类型且不修改原对象", () => {
  const original = { text: "1", count: 1, enabled: false, missing: null,
    nested: { list: [1, "2", true, null, { value: 3 }] } };
  const changed = editor.setAtPath(original, ["nested", "list", 4, "value"], 9.5);

  assert.equal(original.nested.list[4].value, 3);
  assert.equal(changed.nested.list[4].value, 9.5);
  assert.deepEqual(Object.values(changed).map(editor.valueType),
    ["string", "number", "boolean", "null", "object"]);
});

test("对象字段可增删改名且阻止空名和同级重复", () => {
  const source = { nested: { alpha: 1, beta: 2 } };
  const added = editor.addProperty(source, ["nested"], "gamma", false);
  const renamed = editor.renameAtPath(added, ["nested", "gamma"], "ready");
  const removed = editor.removeAtPath(renamed, ["nested", "alpha"]);

  assert.deepEqual(removed, { nested: { beta: 2, ready: false } });
  assert.throws(() => editor.renameAtPath(source, ["nested", "alpha"], ""), /不能为空/);
  assert.throws(() => editor.renameAtPath(source, ["nested", "alpha"], "beta"), /已存在/);
  assert.throws(() => editor.addProperty(source, ["nested"], "alpha", 3), /已存在/);
});

test("数组元素增删和类型切换保持明确语义", () => {
  const added = editor.addArrayItem({ values: [1] }, ["values"], { key: "value" });
  const converted = editor.convertAtPath(added, ["values", 0], "string");
  const removed = editor.removeAtPath(converted, ["values", 1]);

  assert.deepEqual(removed, { values: ["1"] });
});

test("commandId 校验识别非 32 位十六进制和 Action 内重复", () => {
  const valid = "0123456789abcdef0123456789abcdef";
  const issues = editor.commandIdIssues({ steps: [
    { params: { commandId: valid } },
    { params: { nested: { commandId: valid.toUpperCase() } } },
    { params: { commandId: "not-hex" } }
  ] });

  assert.deepEqual(issues.map(issue => issue.type).sort(), ["duplicate", "invalid"]);
  assert.match(issues.find(issue => issue.type === "invalid").path, /steps\[2\]\.params\.commandId/);
});

const test = require("node:test");
const assert = require("node:assert/strict");

const { createEmptyDefinition, describeActionType } = require("../../main/resources/static/action-definition.js");

test("new actions default to global composites and the entry switch is the only type discriminator", () => {
  const definition = createEmptyDefinition();

  assert.equal(definition.entryPoint, false);
  assert.deepEqual(describeActionType(false), {
    label: "全局组合动作",
    description: "可被其他 Action 按精确版本引用"
  });
  assert.deepEqual(describeActionType(true), {
    label: "主 Action",
    description: "可由调度系统直接触发"
  });
});

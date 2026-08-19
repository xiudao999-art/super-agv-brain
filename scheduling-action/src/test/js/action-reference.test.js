const test = require("node:test");
const assert = require("node:assert/strict");

const {
  createActionReference,
  getMissingRequiredBindings,
  listCatalogItems,
  parseBindingValue,
  promoteReferenceInput,
  updateReferenceBinding
} = require("../../main/resources/static/action-reference.js");

test("catalog exposes only published actions in the current scope and excludes the action being edited", () => {
  const items = [
    { actionKey: "ARM.PICK", version: "1.0.0", displayName: "机械臂抓取", scope: "TIANJIN", status: "PUBLISHED", entryPoint: false },
    { actionKey: "ARM.PICK", version: "1.1.0", displayName: "机械臂抓取", scope: "TIANJIN", status: "DEPRECATED", entryPoint: false },
    { actionKey: "MOVE", version: "1.0.0", displayName: "底盘移动", scope: "TIANJIN", status: "PUBLISHED", entryPoint: false },
    { actionKey: "SHANGHAI.MOVE", version: "1.0.0", displayName: "上海移动", scope: "SHANGHAI", status: "PUBLISHED", entryPoint: false },
    { actionKey: "ORDER.FULFILL", version: "1.0.0", displayName: "订单抓取履约", scope: "TIANJIN", status: "PUBLISHED", entryPoint: true }
  ];

  const result = listCatalogItems(items, { scope: "TIANJIN", currentActionKey: "MOVE", query: "抓取" });

  assert.deepEqual(result.map(item => `${item.actionKey}@${item.version}`), ["ARM.PICK@1.0.0"]);
});

test("adding a catalog action pins its version and recommends compatible parent inputs by name", () => {
  const catalogItem = {
    actionKey: "ARM.PICK",
    version: "1.2.0",
    displayName: "机械臂抓取",
    description: "抓取物料",
    defaultTimeoutMs: 90000,
    hasPhysicalSideEffect: true,
    inputSchema: {
      station: { type: "STRING", required: true },
      point: { type: "STRING", required: true },
      speed: { type: "NUMBER", required: false }
    }
  };
  const owner = {
    inputSchema: {
      station: { type: "STRING", required: true },
      speed: { type: "INTEGER", required: false },
      point: { type: "BOOLEAN", required: false }
    }
  };

  const node = createActionReference(catalogItem, owner, ["arm_pick"]);

  assert.equal(node.kind, "ACTION_REF");
  assert.deepEqual(node.actionRef, { actionKey: "ARM.PICK", version: "1.2.0" });
  assert.deepEqual(node.with, { station: "$input.station", speed: "$input.speed" });
  assert.equal(node.stepId, "arm_pick_2");
  assert.equal(node.timeoutMs, 90000);
  assert.equal(node.gate, true);
});

test("a reference can only be created from a published global composite", () => {
  const common = { actionKey: "ARM.PICK", version: "1.2.0", inputSchema: {} };

  assert.throws(
    () => createActionReference({ ...common, status: "PUBLISHED", entryPoint: true }, { inputSchema: {} }),
    /entry action/i);
  assert.throws(
    () => createActionReference({ ...common, status: "DEPRECATED", entryPoint: false }, { inputSchema: {} }),
    /published/i);
});

test("promoting an unbound child parameter adds the parent input and binds the reference in one operation", () => {
  const definition = {
    inputSchema: {},
    steps: [{
      kind: "ACTION_REF",
      stepId: "pick",
      actionRef: { actionKey: "ARM.PICK", version: "1.2.0" },
      with: {}
    }]
  };
  const catalogItem = {
    actionKey: "ARM.PICK",
    version: "1.2.0",
    inputSchema: { station: { type: "STRING", required: true } }
  };

  const result = promoteReferenceInput(definition, [{ key: "steps", index: 0 }], catalogItem, "station");

  assert.deepEqual(result.inputSchema.station, { type: "STRING", required: true });
  assert.equal(result.steps[0].with.station, "$input.station");
  assert.deepEqual(definition.inputSchema, {});
  assert.deepEqual(definition.steps[0].with, {});
});

test("binding edits update the selected nested reference without mutating its siblings", () => {
  const sibling = { kind: "CAPABILITY", stepId: "verify" };
  const definition = {
    steps: [{
      kind: "FOREACH",
      stepId: "loop",
      steps: [
        { kind: "ACTION_REF", stepId: "pick", actionRef: { actionKey: "ARM.PICK", version: "1.2.0" }, with: {} },
        sibling
      ]
    }]
  };
  const path = [{ key: "steps", index: 0 }, { key: "steps", index: 0 }];

  const bound = updateReferenceBinding(definition, path, "point", "$item.slotId");
  const unbound = updateReferenceBinding(bound, path, "point", undefined);

  assert.equal(bound.steps[0].steps[0].with.point, "$item.slotId");
  assert.equal("point" in unbound.steps[0].steps[0].with, false);
  assert.equal(bound.steps[0].steps[1], sibling);
  assert.deepEqual(definition.steps[0].steps[0].with, {});
});

test("binding values preserve schema types instead of saving every editor value as text", () => {
  assert.equal(parseBindingValue("12.5", { type: "NUMBER" }, "LITERAL"), 12.5);
  assert.equal(parseBindingValue("false", { type: "BOOLEAN" }, "LITERAL"), false);
  assert.deepEqual(parseBindingValue('[{"slotId":"A"}]', { type: "ARRAY" }, "LITERAL"), [{ slotId: "A" }]);
  assert.equal(parseBindingValue("$input.station", { type: "STRING" }, "EXPRESSION"), "$input.station");
  assert.throws(() => parseBindingValue("1.2", { type: "INTEGER" }, "LITERAL"), /integer/i);
});

test("required binding status reports only parameters that are truly absent", () => {
  const catalogItem = {
    inputSchema: {
      station: { type: "STRING", required: true },
      point: { type: "STRING", required: true },
      speed: { type: "NUMBER", required: false }
    }
  };
  const node = { with: { station: "$input.station", speed: 0 } };

  assert.deepEqual(getMissingRequiredBindings(node, catalogItem), ["point"]);
});

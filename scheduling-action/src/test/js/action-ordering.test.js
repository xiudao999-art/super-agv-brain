const test = require("node:test");
const assert = require("node:assert/strict");

const {
  areSiblingPaths,
  getNodeAtPath,
  insertNodeAtLocation,
  moveItemAtInsertion,
  moveNodeAtInsertion,
  pathKey,
  removeNodeAtPath,
  replaceNodeAtPath
} = require("../../main/resources/static/action-ordering.js");

test("dragging a node after a later node produces the visible order users expect", () => {
  const nodes = [{ stepId: "safe" }, { stepId: "approach" }, { stepId: "pick" }, { stepId: "retreat" }];

  const result = moveItemAtInsertion(nodes, 0, 3);

  assert.deepEqual(result.items.map(node => node.stepId), ["approach", "pick", "safe", "retreat"]);
  assert.equal(result.toIndex, 2);
  assert.equal(result.changed, true);
});

test("dragging a node before an earlier node preserves object identity", () => {
  const nodes = [{ stepId: "safe" }, { stepId: "approach" }, { stepId: "pick" }, { stepId: "retreat" }];
  const draggedNode = nodes[3];

  const result = moveItemAtInsertion(nodes, 3, 1);

  assert.deepEqual(result.items.map(node => node.stepId), ["safe", "retreat", "approach", "pick"]);
  assert.equal(result.items[1], draggedNode);
  assert.equal(result.toIndex, 1);
});

test("dropping at the current insertion point is a no-op", () => {
  const nodes = [{ stepId: "safe" }, { stepId: "approach" }, { stepId: "pick" }];

  const result = moveItemAtInsertion(nodes, 1, 2);

  assert.deepEqual(result.items, nodes);
  assert.equal(result.toIndex, 1);
  assert.equal(result.changed, false);
});

test("invalid indexes are rejected instead of corrupting the action definition", () => {
  assert.throws(() => moveItemAtInsertion([], 0, 0), RangeError);
  assert.throws(() => moveItemAtInsertion([{}], -1, 0), RangeError);
  assert.throws(() => moveItemAtInsertion([{}], 0, 2), RangeError);
});

test("a subaction is reordered inside its FOREACH body without mutating the source definition", () => {
  const definition = {
    steps: [
      { kind: "CAPABILITY", stepId: "before" },
      {
        kind: "FOREACH",
        stepId: "loop",
        steps: [
          { kind: "CAPABILITY", stepId: "approach" },
          { kind: "CAPABILITY", stepId: "pick" },
          { kind: "CAPABILITY", stepId: "retreat" }
        ]
      }
    ]
  };
  const sourcePath = [{ key: "steps", index: 1 }, { key: "steps", index: 0 }];

  const result = moveNodeAtInsertion(definition, sourcePath, 3);

  assert.deepEqual(result.definition.steps[1].steps.map(node => node.stepId), ["pick", "retreat", "approach"]);
  assert.deepEqual(definition.steps[1].steps.map(node => node.stepId), ["approach", "pick", "retreat"]);
  assert.equal(getNodeAtPath(result.definition, result.nodePath).stepId, "approach");
  assert.equal(pathKey(result.nodePath), "steps:1/steps:2");
});

test("a CONDITION branch is an independent sibling container", () => {
  const thenPath = [{ key: "steps", index: 0 }, { key: "then", index: 0 }];
  const thenSiblingPath = [{ key: "steps", index: 0 }, { key: "then", index: 1 }];
  const elsePath = [{ key: "steps", index: 0 }, { key: "else", index: 0 }];

  assert.equal(areSiblingPaths(thenPath, thenSiblingPath), true);
  assert.equal(areSiblingPaths(thenPath, elsePath), false);
  assert.equal(areSiblingPaths(thenPath, [{ key: "steps", index: 1 }]), false);
});

test("replace and remove operate on nested paths and preserve unrelated branches", () => {
  const untouchedElse = [{ kind: "CAPABILITY", stepId: "fallback" }];
  const definition = {
    steps: [{
      kind: "CONDITION",
      stepId: "decision",
      then: [{ kind: "CAPABILITY", stepId: "inspect" }, { kind: "CAPABILITY", stepId: "accept" }],
      else: untouchedElse
    }]
  };
  const inspectPath = [{ key: "steps", index: 0 }, { key: "then", index: 0 }];
  const replaced = replaceNodeAtPath(definition, inspectPath, { kind: "CAPABILITY", stepId: "inspect_v2" });
  const removed = removeNodeAtPath(replaced, [{ key: "steps", index: 0 }, { key: "then", index: 1 }]);

  assert.equal(getNodeAtPath(replaced, inspectPath).stepId, "inspect_v2");
  assert.deepEqual(removed.definition.steps[0].then.map(node => node.stepId), ["inspect_v2"]);
  assert.equal(removed.definition.steps[0].else, untouchedElse);
  assert.equal(definition.steps[0].then[0].stepId, "inspect");
});

test("an external catalog action can be inserted at a nested canvas location", () => {
  const definition = {
    steps: [{
      kind: "CONDITION",
      stepId: "decision",
      then: [{ kind: "CAPABILITY", stepId: "inspect" }],
      else: []
    }]
  };
  const reference = { kind: "ACTION_REF", stepId: "pick", actionRef: { actionKey: "ARM.PICK", version: "1.0.0" } };

  const result = insertNodeAtLocation(definition, {
    parentPath: [{ key: "steps", index: 0 }],
    containerKey: "then",
    insertionIndex: 0
  }, reference);

  assert.deepEqual(result.definition.steps[0].then.map(node => node.stepId), ["pick", "inspect"]);
  assert.equal(getNodeAtPath(result.definition, result.nodePath), reference);
  assert.deepEqual(definition.steps[0].then.map(node => node.stepId), ["inspect"]);
});

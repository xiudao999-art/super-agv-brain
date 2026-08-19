const test = require("node:test");
const assert = require("node:assert/strict");

const { buildWorkflowLayout } = require("../../main/resources/static/action-canvas.js");

test("canvas layout flattens nested actions while retaining hierarchy and sibling containers", () => {
  const definition = {
    steps: [
      { kind: "CAPABILITY", stepId: "prepare", capabilityKey: "arm.prepare" },
      {
        kind: "CONDITION",
        stepId: "quality_gate",
        then: [{ kind: "CAPABILITY", stepId: "accept", capabilityKey: "quality.accept" }],
        else: [{ kind: "CAPABILITY", stepId: "reject", capabilityKey: "quality.reject" }]
      },
      {
        kind: "FOREACH",
        stepId: "batch",
        steps: [{ kind: "CAPABILITY", stepId: "pick", capabilityKey: "arm.pick" }]
      }
    ]
  };

  const layout = buildWorkflowLayout(definition, [], 760);

  assert.deepEqual(layout.nodes.map(item => item.node.stepId), ["prepare", "quality_gate", "accept", "reject", "batch", "pick"]);
  assert.deepEqual(layout.nodes.map(item => item.depth), [0, 0, 1, 1, 0, 1]);
  assert.equal(layout.nodes[2].branchLabel, "THEN");
  assert.equal(layout.nodes[3].branchLabel, "ELSE");
  assert.equal(layout.nodes[5].branchLabel, "LOOP BODY");
  assert.notEqual(layout.nodes[2].containerId, layout.nodes[3].containerId);
  assert.equal(layout.nodes[0].containerId, layout.nodes[1].containerId);
});

test("canvas layout exposes stable hit rectangles and physical capability metadata", () => {
  const definition = {
    steps: [{ kind: "CAPABILITY", stepId: "move", capabilityKey: "agv.move" }]
  };
  const capabilities = [{ capabilityKey: "agv.move", sideEffect: "PHYSICAL" }];

  const layout = buildWorkflowLayout(definition, capabilities, 640);
  const [node] = layout.nodes;

  assert.equal(node.physical, true);
  assert.ok(node.width >= 300);
  assert.ok(node.height >= 64);
  assert.ok(layout.height > node.y + node.height);
  assert.equal(node.pathKey, "steps:0");
});

test("an action reference is one movable dashed group that previews its compiled atomic actions", () => {
  const definition = {
    steps: [{
      kind: "ACTION_REF",
      stepId: "pick_material",
      displayName: "抓取物料",
      actionRef: { actionKey: "ARM.PICK", version: "1.2.0" },
      with: {}
    }]
  };
  const catalog = [{
    actionKey: "ARM.PICK",
    version: "1.2.0",
    hasPhysicalSideEffect: true,
    inputSchema: {},
    atomicSteps: [
      { stepId: "move_to_pick", displayName: "移动到抓取位", capabilityKey: "arm.move", depth: 0 },
      { stepId: "close_gripper", displayName: "闭合夹爪", capabilityKey: "arm.gripper.close", depth: 0 }
    ]
  }];

  const layout = buildWorkflowLayout(definition, [], 760, catalog);
  const [group] = layout.nodes;

  assert.equal(layout.nodes.length, 1, "preview rows must not become editable nodes");
  assert.equal(group.compositeGroup, true);
  assert.deepEqual(group.atomicSteps.map(step => step.stepId), ["move_to_pick", "close_gripper"]);
  assert.ok(group.height > 68, "the group must reserve canvas space for its atomic preview");
});

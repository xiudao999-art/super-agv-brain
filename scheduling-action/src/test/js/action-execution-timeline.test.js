const test = require("node:test");
const assert = require("node:assert/strict");
const timeline = require("../../main/resources/static/action-execution-timeline.js");

test("renders v2 step evidence and resolved operation parameters", () => {
  const events = [{
    state: "RUNNING", timestamp: "2026-08-25T02:17:30Z",
    stepEvent: {
      eventType: "STEP_SUCCEEDED", stepSequence: 2, stepId: "move-02",
      operation: "MOVE_TO_MAP_POINT", stepState: "SUCCEEDED", attempt: 1,
      durationMs: 4014, evidence: { actualPose: { x: 1000, y: 2000 } }
    }
  }];
  const commandInput = { executionPlan: { steps: [{
    stepId: "move-02", operation: "MOVE_TO_MAP_POINT",
    params: { pointName: "PICK_STATION_A", speed: 0.2 }
  }] } };

  const html = timeline.render(events, [], commandInput);

  assert.match(html, /move-02/);
  assert.match(html, /执行成功/);
  assert.match(html, /4014 ms/);
  assert.match(html, /PICK_STATION_A/);
  assert.match(html, /actualPose/);
});

test("renders persisted resolved steps when no process event exists", () => {
  const html = timeline.render([], [{ stepId: "verify-load", state: "SUCCEEDED" }], {});
  assert.match(html, /verify-load/);
  assert.match(html, /SUCCEEDED/);
});

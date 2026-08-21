const test = require("node:test");
const assert = require("node:assert/strict");
const timeline = require("../../main/resources/static/action-execution-timeline.js");

test("renders structured phase evidence and resolved device parameters", () => {
  const events = [{
    state: "RUNNING",
    timestamp: "2026-08-20T12:37:39Z",
    reportState: { robotState: "EXECUTING" },
    phaseEvent: {
      eventType: "PHASE_SUCCEEDED",
      stepSequence: 2,
      phaseId: "phase-02",
      subAction: "MOVE_TO_MAP_POINT",
      stepState: "SUCCEEDED",
      attempt: 1,
      durationMs: 4014,
      evidence: { actualPose: { x: 1000, y: 2000, yaw: 90, map: "LAB" } }
    }
  }];
  const commandInput = {
    MainAction: {
      phases: [{ phaseId: "phase-02", params: { pointName: "PICK_STATION_A", speed: 0.2 } }]
    }
  };

  const html = timeline.render(events, [], commandInput);

  assert.match(html, /phase-02/);
  assert.match(html, /执行成功/);
  assert.match(html, /4014 ms/);
  assert.match(html, /PICK_STATION_A/);
  assert.match(html, /actualPose/);
  assert.match(html, /1000/);
});

test("falls back to resolved steps for old clients without phaseEvent", () => {
  const html = timeline.render([], [{ phaseId: "legacy-step", state: "SUCCEEDED" }], {});
  assert.match(html, /legacy-step/);
  assert.match(html, /SUCCEEDED/);
});

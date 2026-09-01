const test = require("node:test");
const assert = require("node:assert/strict");
const motion = require("../../main/resources/static/action-relative-motion.js");

const probe = {
  robotId: "R01", capturedAt: "2026-09-01T12:00:00Z", armMoveRequestType: 1, speedPercent: 30,
  armPoseXYZRxRyRz: { x: 100, y: 200, z: 300, rx: 1, ry: 2, rz: 3 },
  armPoseJ1J2J3J4J5J6: { j1: 10, j2: 20, j3: 30, j4: 40, j5: 50, j6: 60 }
};

test("当前位置必须同时包含两套位姿且属于所选机器人", () => {
  const normalized = motion.normalizeProbe("R01", probe);
  assert.equal(normalized.cartesian.x, 100);
  assert.equal(normalized.joint.j6, 60);
  assert.throws(() => motion.normalizeProbe("R02", probe), /不一致/);
  assert.throws(() => motion.normalizeProbe("R01", { ...probe, armPoseXYZRxRyRz: null }), /笛卡尔/);
});

test("笛卡尔和关节相对偏移分别计算绝对目标", () => {
  const current = motion.normalizeProbe("R01", probe);
  const cartesian = motion.calculate("cartesian", current.cartesian,
    { x: 5, y: -10, z: 0.25, rx: 0, ry: 1.5, rz: -3 });
  const joint = motion.calculate("joint", current.joint,
    { j1: -1, j2: -2, j3: -3, j4: -4, j5: -5, j6: -6 });

  assert.deepEqual(cartesian, { x: 105, y: 190, z: 300.25, rx: 1, ry: 3.5, rz: 0 });
  assert.deepEqual(joint, { j1: 9, j2: 18, j3: 27, j4: 36, j5: 45, j6: 54 });
});

test("应用一套目标位姿时保留另一套原值", () => {
  const params = { armMoveRequestParams: {
    armPoseXYZRxRyRz: { x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 },
    armPoseJ1J2J3J4J5J6: { j1: 1, j2: 2, j3: 3, j4: 4, j5: 5, j6: 6 }
  } };
  const result = motion.applyTarget(params, "cartesian", probe.armPoseXYZRxRyRz);

  assert.deepEqual(result.armMoveRequestParams.armPoseXYZRxRyRz, probe.armPoseXYZRxRyRz);
  assert.deepEqual(result.armMoveRequestParams.armPoseJ1J2J3J4J5J6,
    params.armMoveRequestParams.armPoseJ1J2J3J4J5J6);
  assert.notEqual(result, params);
});

test("切换机器人后重建临时相对运动状态并清除旧基准", () => {
  const transient = motion.createTransientState();
  transient.probe = motion.normalizeProbe("R01", probe);
  transient.offsets.cartesian.x = 25;

  const afterRobotSwitch = motion.createTransientState();

  assert.equal(afterRobotSwitch.probe, null);
  assert.equal(afterRobotSwitch.offsets.cartesian.x, 0);
  assert.equal(afterRobotSwitch.mode, "cartesian");
});

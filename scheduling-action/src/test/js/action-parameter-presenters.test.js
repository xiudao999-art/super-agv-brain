const test = require("node:test");
const assert = require("node:assert/strict");
const presenters = require("../../main/resources/static/action-parameter-presenters.js");

test("operation 别名只决定展示器家族", () => {
  assert.equal(presenters.resolve("MOVE_TO_POSE"), "arm");
  assert.equal(presenters.resolve("GRIP.OPEN"), "gripper");
  assert.equal(presenters.resolve("GRIP_OPEN"), "gripper");
  assert.equal(presenters.resolve("GRIP"), "gripper");
  assert.equal(presenters.resolve("VENDOR.CUSTOM_MOVE"), "generic");
  assert.equal(presenters.normalizeOperation("grip.verify_load"), "GRIP_VERIFY_LOAD");
});

test("可扩展展示器提供不改写协议值的中文字段说明", () => {
  const operation = "GRIP.OPEN";
  presenters.resolve(operation);
  assert.equal(operation, "GRIP.OPEN");
  assert.match(presenters.describeField("forcePercent"), /夹持力/);
});

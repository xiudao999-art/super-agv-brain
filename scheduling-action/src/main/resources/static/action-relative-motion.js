(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionRelativeMotion = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const MODES = Object.freeze({ CARTESIAN: "cartesian", JOINT: "joint" });
  const FIELDS = Object.freeze({
    cartesian: Object.freeze(["x", "y", "z", "rx", "ry", "rz"]),
    joint: Object.freeze(["j1", "j2", "j3", "j4", "j5", "j6"])
  });

  function emptyOffsets(mode) {
    return Object.fromEntries(fieldsFor(mode).map(field => [field, 0]));
  }

  function createTransientState() {
    return {
      mode: MODES.CARTESIAN,
      probe: null,
      probing: false,
      offsets: {
        cartesian: emptyOffsets(MODES.CARTESIAN),
        joint: emptyOffsets(MODES.JOINT)
      }
    };
  }

  function normalizeProbe(robotId, value) {
    if (!value || typeof value !== "object") throw new Error("当前位置响应不完整。");
    if (!robotId || value.robotId !== robotId) throw new Error("当前位置与所选机器人不一致。");
    return {
      robotId,
      capturedAt: value.capturedAt || null,
      armMoveRequestType: requireInteger(value.armMoveRequestType, "armMoveRequestType"),
      speedPercent: requireInteger(value.speedPercent, "speedPercent"),
      cartesian: normalizePose(value.armPoseXYZRxRyRz, FIELDS.cartesian, "笛卡尔位姿"),
      joint: normalizePose(value.armPoseJ1J2J3J4J5J6, FIELDS.joint, "六轴关节位姿")
    };
  }

  function calculate(mode, baseline, offsets) {
    const fields = fieldsFor(mode);
    if (!baseline || typeof baseline !== "object") throw new Error("请先获取机械臂当前位置。");
    const result = {};
    fields.forEach(field => {
      const current = requireNumber(baseline[field], `当前值 ${field}`);
      const offset = requireNumber(offsets && offsets[field], `相对偏移 ${field}`);
      // 把浮点误差约束在工程界面可用的小数精度内。
      result[field] = Number((current + offset).toFixed(9));
    });
    return result;
  }

  function applyTarget(parameters, mode, target) {
    const result = clone(parameters || {});
    if (!result.armMoveRequestParams || typeof result.armMoveRequestParams !== "object"
        || Array.isArray(result.armMoveRequestParams)) {
      result.armMoveRequestParams = {};
    }
    const field = mode === MODES.CARTESIAN
      ? "armPoseXYZRxRyRz" : mode === MODES.JOINT ? "armPoseJ1J2J3J4J5J6" : null;
    if (!field) throw new Error("不支持的相对运动模式。");
    result.armMoveRequestParams[field] = normalizePose(target, fieldsFor(mode), "计算目标");
    return result;
  }

  function fieldsFor(mode) {
    const fields = FIELDS[mode];
    if (!fields) throw new Error("不支持的相对运动模式。");
    return fields;
  }

  function normalizePose(value, fields, label) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error(`${label}必须是 JSON 对象。`);
    }
    return Object.fromEntries(fields.map(field => [field, requireNumber(value[field], `${label}.${field}`)]));
  }

  function requireInteger(value, label) {
    if (!Number.isInteger(value)) throw new Error(`${label}必须是整数。`);
    return value;
  }

  function requireNumber(value, label) {
    const number = typeof value === "number" ? value : Number(value);
    if (!Number.isFinite(number)) throw new Error(`${label}必须是有限数字。`);
    return number;
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  return { MODES, FIELDS, emptyOffsets, createTransientState, normalizeProbe, calculate, applyTarget };
});

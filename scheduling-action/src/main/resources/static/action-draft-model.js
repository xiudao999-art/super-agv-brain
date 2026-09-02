(function (root, factory) {
  const api = factory(
    typeof module === "object" && module.exports ? require("./action-parameter-editor.js") : root.ActionParameterEditor
  );
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionDraftModel = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function (ParameterEditor) {
  "use strict";

  function applyJson(text, currentDefinition) {
    let definition;
    try { definition = JSON.parse(text); }
    catch (_) { throw new Error("完整 Action JSON 必须是合法的 JSON 对象。"); }
    validate(definition, currentDefinition, true);
    return clone(definition);
  }

  function validate(definition, currentDefinition, validateCommandIds) {
    if (!definition || Array.isArray(definition) || typeof definition !== "object") {
      throw new Error("Action 必须是 JSON 对象。");
    }
    if (currentDefinition && definition.id && definition.id !== currentDefinition.id) {
      throw new Error("不能通过 JSON 修改已有 Action 的 id。");
    }
    if (currentDefinition && Boolean(definition.enabled) !== Boolean(currentDefinition.enabled)) {
      throw new Error("enabled 只能通过启用/停用操作修改。");
    }
    if (!currentDefinition && definition.id != null) throw new Error("新建 Action 的 id 必须为 null。");
    if (!currentDefinition && definition.enabled !== false) throw new Error("新建 Action 的 enabled 必须为 false。");
    if (typeof definition.name !== "string" || !definition.name.trim()) throw new Error("Action 名称不能为空。");
    if (!Number.isInteger(definition.timeoutMs) || definition.timeoutMs < 1000 || definition.timeoutMs > 3600000) {
      throw new Error("总超时必须是 1000-3600000 之间的整数。");
    }
    if (!Array.isArray(definition.steps)) throw new Error("steps 必须是数组。");
    definition.steps.forEach((step, index) => validateStep(step, index));
    if (validateCommandIds) {
      const issues = ParameterEditor.commandIdIssues(definition);
      if (issues.length) throw new Error(issues[0].message);
    }
    return true;
  }

  function validateStep(step, index) {
    if (!step || Array.isArray(step) || typeof step !== "object") throw new Error(`步骤 ${index + 1} 必须是 JSON 对象。`);
    if (typeof step.stepId !== "string" || !step.stepId.trim()) throw new Error(`步骤 ${index + 1} 的 stepId 不能为空。`);
    if (typeof step.operation !== "string" || !step.operation.trim()) throw new Error(`步骤 ${index + 1} 的 operation 不能为空。`);
    if (!step.params || Array.isArray(step.params) || typeof step.params !== "object") throw new Error(`步骤 ${index + 1} 的 params 必须是 JSON 对象。`);
    if (typeof step.gate !== "boolean") throw new Error(`步骤 ${index + 1} 的 gate 必须是布尔值。`);
    if (!step.onFailure || Array.isArray(step.onFailure) || typeof step.onFailure !== "object") {
      throw new Error(`步骤 ${index + 1} 的 onFailure 必须是 JSON 对象。`);
    }
  }

  /** 保存只截取当前草稿，不执行内容校验；完整校验统一延后到启用和执行阶段。 */
  function snapshotForSave(definition) {
    return clone(definition);
  }

  function clone(value) { return JSON.parse(JSON.stringify(value)); }

  return { applyJson, validate, snapshotForSave };
});

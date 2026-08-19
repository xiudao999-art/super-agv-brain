(function exposeActionDefinition(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionDefinition = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionDefinitionApi() {
  "use strict";

  function createEmptyDefinition() {
    return {
      schemaVersion: "1.0",
      actionKey: "",
      version: "1.0.0",
      displayName: "新建机器人动作",
      description: "",
      // 非入口 Action 就是全局组合动作，不存在额外的类型或所属主 Action。
      entryPoint: false,
      scope: "TIANJIN",
      inputSchema: {},
      outputSchema: {},
      steps: [],
      defaultPolicy: { timeoutMs: 60000, onFailure: { strategy: "HOLD", maxRetries: 0 } },
      labels: {}
    };
  }

  function describeActionType(entryPoint) {
    return entryPoint
      ? { label: "主 Action", description: "可由调度系统直接触发" }
      : { label: "全局组合动作", description: "可被其他 Action 按精确版本引用" };
  }

  return Object.freeze({ createEmptyDefinition, describeActionType });
});

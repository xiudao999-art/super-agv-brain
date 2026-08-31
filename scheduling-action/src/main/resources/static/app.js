(function () {
  "use strict";

  const $ = id => document.getElementById(id);
  const TASK_KEY = "kunling.action.currentExecution";
  const state = {
    actions: [], robots: [], current: null, steps: [], preview: null,
    execution: null, executionEvents: [], workbench: ActionWorkbenchState.create(readSessionTask()),
    pollTimer: null
  };

  document.addEventListener("DOMContentLoaded", initialise);

  async function initialise() {
    bindEvents();
    try {
      await loadRobots();
      await loadActions();
      renderConnectionState();
    } catch (error) { report(error); }
  }

  function bindEvents() {
    $("actionSelect").addEventListener("change", () => selectAction($("actionSelect").value));
    $("robotSelect").addEventListener("change", () => { invalidatePreview(); renderSteps(); applyLocks(); });
    $("newActionButton").addEventListener("click", newAction);
    $("saveActionButton").addEventListener("click", saveAction);
    $("enableButton").addEventListener("click", toggleEnabled);
    $("addStepButton").addEventListener("click", () => addStep());
    $("previewButton").addEventListener("click", previewPackage);
    $("executeButton").addEventListener("click", startExecution);
    document.body.addEventListener("input", invalidatePreview);
    document.body.addEventListener("change", invalidatePreview);
  }

  async function loadRobots() {
    state.robots = await api("/api/robots");
    fillSelect($("robotSelect"), state.robots.map(robot => ({
      value: robot.robotId, label: `${robot.robotId} · ${robot.robotType || "ROBOT"}`
    })), "没有在线机器人");
  }

  async function loadActions(preferredId) {
    state.actions = await api("/api/actions");
    fillSelect($("actionSelect"), state.actions.map(item => ({
      value: item.definition.id,
      label: `${item.definition.name} · ${item.definition.enabled ? "已启用" : "未启用"}`
    })), "请选择 Action");
    const id = preferredId || state.current && state.current.definition.id
      || state.actions[0] && state.actions[0].definition.id;
    if (id) await selectAction(id); else newAction();
  }

  async function selectAction(id) {
    if (!id) return;
    state.current = await api(`/api/actions/${encodeURIComponent(id)}`);
    const definition = state.current.definition;
    $("actionSelect").value = definition.id;
    $("actionName").value = definition.name;
    $("timeoutMs").value = definition.timeoutMs;
    $("actionEnabled").textContent = definition.enabled ? "已启用" : "未启用";
    $("actionId").textContent = `ID ${definition.id}`;
    $("enableButton").textContent = definition.enabled ? "停用" : "启用";
    state.steps = clone(definition.steps || []);
    state.workbench.serverLocked = Boolean(state.current.executionLocked);
    state.preview = null;
    renderSteps(); renderPreview(); applyLocks(); restoreExecutionIfNeeded();
  }

  function newAction() {
    if (!canEdit()) return toast("机器人离线或当前 Action 正在执行。");
    state.current = null;
    $("actionSelect").value = "";
    $("actionName").value = "";
    $("timeoutMs").value = "60000";
    $("actionEnabled").textContent = "未启用";
    $("actionId").textContent = "ID -";
    $("enableButton").textContent = "启用";
    state.steps = [];
    state.preview = null;
    renderSteps(); renderPreview(); applyLocks();
  }

  async function saveAction() {
    try {
      const definition = readDefinition();
      state.current = state.current
        ? await api(`/api/actions/${encodeURIComponent(state.current.definition.id)}`,
          { method: "PUT", body: definition })
        : await api("/api/actions", { method: "POST", body: definition });
      toast("Action 定义已保存。");
      await loadActions(state.current.definition.id);
    } catch (error) { report(error); }
  }

  async function toggleEnabled() {
    if (!state.current) return toast("请先保存 Action。");
    try {
      const definition = state.current.definition;
      const suffix = definition.enabled ? "disable"
        : `enable?robotId=${encodeURIComponent(selectedRobotId())}`;
      state.current = await api(`/api/actions/${encodeURIComponent(definition.id)}/${suffix}`,
        { method: "POST" });
      await selectAction(state.current.definition.id);
    } catch (error) { report(error); }
  }

  function readDefinition() {
    const currentDefinition = state.current && state.current.definition;
    const name = $("actionName").value.trim();
    if (!name) throw new Error("Action 名称不能为空。");
    return {
      id: currentDefinition ? currentDefinition.id : null,
      name,
      enabled: currentDefinition ? currentDefinition.enabled : false,
      timeoutMs: Number($("timeoutMs").value),
      steps: readSteps()
    };
  }

  function addStep(source) {
    const index = state.steps.length + 1;
    const operation = availableOperations()[0] || "MOVE_TO_MAP_POINT";
    state.steps.push(source ? clone(source) : {
      stepId: `step-${String(index).padStart(2, "0")}`,
      operation, params: {}, gate: true,
      onFailure: { rules: [], defaultDirective: { action: "STOP_AND_REPORT", maxRetries: 0, delayMs: 0 } }
    });
    renderSteps(); invalidatePreview();
  }

  function renderSteps() {
    const list = $("stepList"); list.innerHTML = "";
    const operations = availableOperations();
    state.steps.forEach((step, index) => {
      const options = Array.from(new Set([step.operation].concat(operations))).filter(Boolean)
        .map(value => `<option value="${escapeHtml(value)}" ${value === step.operation ? "selected" : ""}>${escapeHtml(value)}</option>`).join("");
      const card = document.createElement("article"); card.className = "step"; card.dataset.index = index;
      card.innerHTML = `<div class="step-head"><span class="step-index">${String(index + 1).padStart(2, "0")}</span>
        <strong>子动作 ${index + 1}</strong>
        <div class="step-tools"><button data-op="up">↑</button><button data-op="down">↓</button><button data-op="copy">复制</button><button data-op="delete">删除</button></div></div>
        <div class="step-grid">
          <label>Step ID<input class="step-id" value="${escapeHtml(step.stepId || "")}" data-editable></label>
          <label>Operation<select class="step-operation" data-editable>${options}</select></label>
        </div>
        <label class="check"><input class="step-gate" type="checkbox" ${step.gate ? "checked" : ""} data-editable> gate（失败时禁止进入后续正常步骤）</label>
        <label>固定参数 params（JSON）<textarea class="step-params code params" data-editable>${escapeHtml(pretty(step.params || {}))}</textarea></label>
        <label>失败策略 onFailure（JSON）<textarea class="step-failure code params" data-editable>${escapeHtml(pretty(step.onFailure || { rules: [], defaultDirective: { action: "STOP_AND_REPORT" } }))}</textarea></label>`;
      card.addEventListener("click", event => handleStepOperation(event, index));
      list.appendChild(card);
    });
    applyLocks();
  }

  function handleStepOperation(event, index) {
    const operation = event.target.dataset.op;
    if (!operation || !canEdit()) return;
    state.steps = readSteps();
    if (operation === "delete") state.steps.splice(index, 1);
    if (operation === "copy") {
      const copy = clone(state.steps[index]); copy.stepId = `${copy.stepId}-copy`;
      state.steps.splice(index + 1, 0, copy);
    }
    if (operation === "up" && index > 0) [state.steps[index - 1], state.steps[index]] = [state.steps[index], state.steps[index - 1]];
    if (operation === "down" && index < state.steps.length - 1) [state.steps[index + 1], state.steps[index]] = [state.steps[index], state.steps[index + 1]];
    renderSteps(); invalidatePreview();
  }

  function readSteps() { return Array.from($("stepList").children).map(readStep); }
  function readStep(card, index) {
    return {
      stepId: card.querySelector(".step-id").value.trim(),
      operation: card.querySelector(".step-operation").value,
      params: parseJson(card.querySelector(".step-params").value, `步骤 ${index + 1} 参数`),
      gate: card.querySelector(".step-gate").checked,
      onFailure: parseJson(card.querySelector(".step-failure").value, `步骤 ${index + 1} 失败策略`)
    };
  }

  async function previewPackage() {
    if (!state.current) return toast("请先保存 Action。");
    try {
      state.preview = await api("/api/action-executions/preview", { method: "POST", body: {
        actionDefinitionId: state.current.definition.id, robotId: selectedRobotId()
      }});
      renderPreview();
    } catch (error) { report(error); }
  }

  function renderPreview() {
    $("packagePreview").textContent = state.preview ? pretty(state.preview.commandInput) : "尚未生成预览";
    $("previewMeta").innerHTML = state.preview
      ? `<span class="badge">Hash ${escapeHtml(state.preview.packageHash.slice(0, 12))}…</span><span class="badge">超时 ${state.preview.timeoutMs}ms</span>` : "";
    $("executeButton").disabled = !state.preview || !state.current
      || !state.current.definition.enabled || !canEdit();
  }

  async function startExecution() {
    if (!state.preview || !window.confirm("确认下发当前动作包？")) return;
    try {
      const command = { actionInstanceId: window.crypto.randomUUID(),
        actionDefinitionId: state.current.definition.id, robotId: selectedRobotId() };
      const receipt = await api("/api/action-executions", { method: "POST", body: command });
      state.execution = await api(`/api/action-executions/${encodeURIComponent(receipt.actionInstanceId)}`);
      state.executionEvents = [];
      ActionWorkbenchState.lockForExecution(state.workbench,
        state.current.definition.id, receipt.actionInstanceId);
      sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench));
      applyLocks(); renderExecution(); schedulePoll();
    } catch (error) { report(error); }
  }

  function restoreExecutionIfNeeded() {
    if (!state.workbench.executionId
        || state.workbench.actionDefinitionId !== (state.current && state.current.definition.id)) {
      renderExecution(); return;
    }
    refreshExecution(state.workbench.executionId).then(() => {
      const released = releaseExecutionLockIfTerminal(); renderExecution();
      if (!released && state.workbench.executionLocked) schedulePoll();
    }).catch(report);
  }

  function schedulePoll() {
    clearTimeout(state.pollTimer); if (!state.execution) return;
    state.pollTimer = setTimeout(async () => {
      let released = false;
      try { await refreshExecution(state.execution.actionInstanceId); released = releaseExecutionLockIfTerminal(); renderExecution(); }
      catch (error) { report(error); }
      if (!released && state.workbench.executionLocked) schedulePoll();
    }, 1200);
  }

  async function refreshExecution(id) {
    const encoded = encodeURIComponent(id);
    [state.execution, state.executionEvents] = await Promise.all([
      api(`/api/action-executions/${encoded}`),
      api(`/api/action-executions/${encoded}/events?limit=500`)
    ]);
  }

  function releaseExecutionLockIfTerminal() {
    if (!ActionWorkbenchState.releaseAfterTerminal(state.workbench, state.execution)) return false;
    sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench)); applyLocks();
    toast(state.execution.state === "UNKNOWN_HOLD"
      ? "Action 已终止并解除定义锁；现场处置仍需人工闭环。" : "Action 已结束，定义已解锁。");
    return true;
  }

  function renderExecution() {
    if (!state.execution) {
      $("executionSummary").textContent = "当前 Action 尚未执行";
      $("stepTimeline").innerHTML = ""; return;
    }
    const error = state.execution.error || {};
    $("executionSummary").innerHTML = `<strong>${escapeHtml(state.execution.state)}</strong> · ${escapeHtml(state.execution.actionInstanceId)} · 物理结果 ${escapeHtml(state.execution.physicalOutcome)}`
      + (error.message ? `<div class="execution-error"><b>失败原因：</b>${escapeHtml(error.message)}</div>` : "");
    const steps = Array.isArray(state.execution.resolvedSteps) ? state.execution.resolvedSteps : [];
    $("stepTimeline").innerHTML = ActionExecutionTimeline.render(
      state.executionEvents, steps, state.execution.commandInput);
  }

  function selectedRobot() {
    return state.robots.find(robot => robot.robotId === $("robotSelect").value) || null;
  }
  function selectedRobotId() {
    const robot = selectedRobot();
    if (!robot) throw new Error("必须选择在线机器人。");
    return robot.robotId;
  }
  function availableOperations() {
    const robot = selectedRobot();
    return robot ? Object.keys(robot.operationCapabilities || {}) : [];
  }
  function canEdit() { return ActionWorkbenchState.canEdit(state.workbench, Boolean(selectedRobot())); }

  function applyLocks() {
    const editable = canEdit();
    document.querySelectorAll("[data-editable]").forEach(element => { element.disabled = !editable; });
    $("actionSelect").disabled = state.workbench.executionLocked;
    $("robotSelect").disabled = state.workbench.executionLocked;
    $("newActionButton").disabled = !editable;
    $("saveActionButton").disabled = !editable;
    $("enableButton").disabled = !editable || !state.current;
    $("lockBanner").hidden = !state.workbench.executionLocked;
    renderPreview(); renderConnectionState();
  }

  function renderConnectionState() {
    $("connectionState").textContent = selectedRobot()
      ? `机器人在线 · ${selectedRobot().robotId}` : "无在线机器人";
  }
  function invalidatePreview() { if (state.preview) { state.preview = null; renderPreview(); } }
  async function api(url, options) {
    const request = Object.assign({ headers: { "Content-Type": "application/json" } }, options || {});
    if (request.body && typeof request.body !== "string") request.body = JSON.stringify(request.body);
    const response = await fetch(url, request);
    if (response.status === 204) return null;
    const body = (response.headers.get("content-type") || "").includes("json") ? await response.json() : await response.text();
    if (!response.ok) throw new Error(typeof body === "string" ? body : body.message || body.error || `HTTP ${response.status}`);
    return body;
  }
  function fillSelect(select, values, emptyLabel) {
    select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      + values.map(item => `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`).join("");
    if (values.length) select.value = values[0].value;
  }
  function parseJson(value, label) { try { const result = JSON.parse(value || "{}"); if (!result || Array.isArray(result) || typeof result !== "object") throw new Error(); return result; } catch (_) { throw new Error(`${label} 必须是合法的 JSON 对象。`); } }
  function pretty(value) { return JSON.stringify(value == null ? {} : value, null, 2); }
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function escapeHtml(value) { return String(value == null ? "" : value).replace(/[&<>"']/g, char => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", "\"":"&quot;", "'":"&#39;" }[char])); }
  function readSessionTask() { try { return JSON.parse(sessionStorage.getItem(TASK_KEY) || "null"); } catch (_) { return null; } }
  function toast(message) { const node = $("toast"); node.textContent = message; node.classList.add("show"); setTimeout(() => node.classList.remove("show"), 3200); }
  function report(error) { console.error(error); toast(error.message || String(error)); }
})();

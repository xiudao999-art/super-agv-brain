(function () {
  "use strict";

  const $ = id => document.getElementById(id);
  const TASK_KEY = "kunling.action.currentCommissioningTask";
  const state = {
    catalog: { actionTypes: [], subActions: [], failureActions: [], retryExhaustedActions: [] },
    actions: [], current: null, phases: [], parameterSets: [], parameterSet: null,
    preview: null, execution: null,
    workbench: ActionWorkbenchState.create(readSessionTask()), pollTimer: null
  };

  document.addEventListener("DOMContentLoaded", initialise);

  async function initialise() {
    bindEvents();
    try {
      state.catalog = await api("/api/action-protocol-catalog");
      fillSelect($("downstreamActionType"), state.catalog.actionTypes.map(item => item.name));
      await loadActions();
      $("connectionState").textContent = "服务已连接";
    } catch (error) { report(error); }
  }

  function bindEvents() {
    $("actionSelect").addEventListener("change", () => selectAction($("actionSelect").value));
    $("newActionButton").addEventListener("click", newAction);
    $("newTaskButton").addEventListener("click", newCommissioningTask);
    $("saveActionButton").addEventListener("click", saveAction);
    $("activateButton").addEventListener("click", activateAction);
    $("addPhaseButton").addEventListener("click", () => addPhase());
    $("saveParameterSetButton").addEventListener("click", saveParameterSet);
    $("parameterSetSelect").addEventListener("change", selectParameterSet);
    $("previewButton").addEventListener("click", previewPackage);
    $("executeButton").addEventListener("click", startExecution);
    $("downstreamActionType").addEventListener("change", () => {
      state.phases = readPhases();
      renderPhases();
    });
    document.body.addEventListener("input", invalidatePreview);
    document.body.addEventListener("change", invalidatePreview);
  }

  async function loadActions(preferredKey) {
    state.actions = await api("/api/actions");
    const options = state.actions.map(item => ({ value: item.actionKey, label: `${item.definition.displayName} · ${item.actionKey}` }));
    fillSelect($("actionSelect"), options, "请选择 Action");
    const key = preferredKey || (state.current && state.current.actionKey) || (state.actions[0] && state.actions[0].actionKey);
    if (key) await selectAction(key);
    else newAction();
  }

  async function selectAction(actionKey) {
    if (!actionKey) return;
    state.current = await api(`/api/actions/${encodeURIComponent(actionKey)}`);
    const definition = state.current.definition;
    $("actionSelect").value = actionKey;
    $("actionKey").value = definition.actionKey;
    $("actionKey").disabled = true;
    $("downstreamActionType").value = definition.downstreamActionType;
    $("displayName").value = definition.displayName || "";
    $("description").value = definition.description || "";
    $("timeoutMs").value = definition.timeoutMs;
    $("inputSchema").value = pretty(definition.inputSchema || {});
    $("parameterSchema").value = pretty(definition.parameterSchema || {});
    state.phases = clone(definition.phases || []);
    renderPhases();
    $("actionStatus").textContent = state.current.status;
    $("actionRevision").textContent = `revision ${state.current.revision}`;
    $("activateButton").textContent = state.current.status === "ACTIVE" ? "停用" : "启用";
    state.workbench.serverLocked = Boolean(state.current.executionLocked);
    state.preview = null;
    renderPreview();
    await loadParameterSets(actionKey);
    applyLocks();
    restoreExecutionIfNeeded(actionKey);
  }

  function newAction() {
    if (!ActionWorkbenchState.canEdit(state.workbench)) return toast("当前联调任务已锁定，请先新建联调任务。");
    state.current = null;
    $("actionSelect").value = "";
    $("actionKey").disabled = false;
    $("actionKey").value = "";
    $("displayName").value = "";
    $("description").value = "";
    $("timeoutMs").value = "60000";
    $("inputSchema").value = "{}";
    $("parameterSchema").value = "{}";
    $("downstreamActionType").value = state.catalog.actionTypes[0] ? state.catalog.actionTypes[0].name : "MOVE";
    state.phases = [];
    state.parameterSets = [];
    state.parameterSet = null;
    renderPhases(); renderParameterSets(); invalidatePreview();
    $("actionStatus").textContent = "NEW";
    $("actionRevision").textContent = "revision -";
    $("activateButton").textContent = "启用";
    applyLocks();
  }

  async function saveAction() {
    try {
      const definition = readDefinition();
      const body = { expectedRevision: state.current ? state.current.revision : null, definition };
      state.current = state.current
        ? await api(`/api/actions/${encodeURIComponent(state.current.actionKey)}`, { method: "PUT", body })
        : await api("/api/actions", { method: "POST", body });
      toast("Action 已保存，状态为 DRAFT。");
      await loadActions(state.current.actionKey);
    } catch (error) { report(error); }
  }

  async function activateAction() {
    if (!state.current) return toast("请先保存 Action。");
    try {
      const operation = state.current.status === "ACTIVE" ? "disable" : "activate";
      state.current = await api(`/api/actions/${encodeURIComponent(state.current.actionKey)}/${operation}?expectedRevision=${state.current.revision}`, { method: "POST" });
      toast(operation === "activate" ? "Action 已启用，可生成执行包。" : "Action 已停用。");
      await selectAction(state.current.actionKey);
    } catch (error) { report(error); }
  }

  function readDefinition() {
    const actionKey = $("actionKey").value.trim();
    if (!actionKey) throw new Error("Action Key 不能为空。");
    return {
      schemaVersion: "1.0", actionKey,
      downstreamActionType: $("downstreamActionType").value,
      displayName: $("displayName").value.trim(), description: $("description").value.trim(),
      inputSchema: parseJson($("inputSchema").value, "业务入参 Schema"),
      parameterSchema: parseJson($("parameterSchema").value, "联调参数 Schema"),
      phases: readPhases(), timeoutMs: Number($("timeoutMs").value)
    };
  }

  function addPhase(source) {
    const index = state.phases.length + 1;
    state.phases.push(source ? clone(source) : {
      phaseId: `phase-${String(index).padStart(2, "0")}`, displayName: `步骤 ${index}`,
      subAction: allowedSubActions()[0] || "MOVE_TO_MAP_POINT", enabled: true, params: {},
      gate: false, onFail: "ABORT", maxRetries: 0, retryFromPhaseId: null, onExhaust: "HOLD"
    });
    renderPhases(); invalidatePreview();
  }

  function renderPhases() {
    const list = $("phaseList"); list.innerHTML = "";
    state.phases.forEach((phase, index) => {
      const card = document.createElement("article"); card.className = "phase"; card.dataset.index = index;
      card.innerHTML = `<div class="phase-head"><span class="phase-index">${String(index + 1).padStart(2, "0")}</span>
        <input class="phase-name" value="${escapeHtml(phase.displayName || "")}" data-editable aria-label="步骤名称">
        <div class="phase-tools"><button data-op="up">↑</button><button data-op="down">↓</button><button data-op="copy">复制</button><button data-op="delete">删除</button></div></div>
        <div class="phase-grid">
          <label>Phase ID<input class="phase-id" value="${escapeHtml(phase.phaseId || "")}" data-editable></label>
          <label>子动作<select class="phase-subaction" data-editable>${optionsHtml(allowedSubActions(), phase.subAction)}</select></label>
          <label>异常策略<select class="phase-onfail" data-editable>${optionsHtml(state.catalog.failureActions, phase.onFail || "ABORT")}</select></label>
          <label>最大重试数<input class="phase-retries" type="number" min="0" max="10" value="${phase.maxRetries || 0}" data-editable></label>
          <label>从步骤重试<input class="phase-retryfrom" value="${escapeHtml(phase.retryFromPhaseId || "")}" placeholder="仅 VERIFY_BEFORE_RETRY" data-editable></label>
          <label>重试耗尽<select class="phase-exhaust" data-editable>${optionsHtml(state.catalog.retryExhaustedActions, phase.onExhaust || "HOLD")}</select></label>
        </div>
        <label class="check"><input class="phase-enabled" type="checkbox" ${phase.enabled !== false ? "checked" : ""} data-editable> 启用</label>
        <label class="check"><input class="phase-gate" type="checkbox" ${phase.gate ? "checked" : ""} data-editable> 验收门禁步骤</label>
        <label>子动作参数（JSON，可用 $input.xxx / $parameters.xxx）<textarea class="phase-params code params" data-editable>${escapeHtml(pretty(phase.params || {}))}</textarea></label>`;
      card.addEventListener("click", event => handlePhaseOperation(event, index));
      list.appendChild(card);
    });
    applyLocks();
  }

  function handlePhaseOperation(event, index) {
    const operation = event.target.dataset.op;
    if (!operation || !ActionWorkbenchState.canEdit(state.workbench)) return;
    state.phases = readPhases();
    if (operation === "delete") state.phases.splice(index, 1);
    if (operation === "copy") {
      const copy = clone(readPhase($("phaseList").children[index], index));
      copy.phaseId = `${copy.phaseId}-copy`;
      state.phases.splice(index + 1, 0, copy);
    }
    if (operation === "up" && index > 0) [state.phases[index - 1], state.phases[index]] = [state.phases[index], state.phases[index - 1]];
    if (operation === "down" && index < state.phases.length - 1) [state.phases[index + 1], state.phases[index]] = [state.phases[index], state.phases[index + 1]];
    renderPhases(); invalidatePreview();
  }

  function readPhases() {
    return Array.from($("phaseList").children).map(readPhase);
  }

  function readPhase(card, index) {
    return {
      phaseId: card.querySelector(".phase-id").value.trim(),
      displayName: card.querySelector(".phase-name").value.trim(),
      subAction: card.querySelector(".phase-subaction").value,
      enabled: card.querySelector(".phase-enabled").checked,
      params: parseJson(card.querySelector(".phase-params").value, `步骤 ${index + 1} 参数`),
      gate: card.querySelector(".phase-gate").checked,
      onFail: card.querySelector(".phase-onfail").value,
      maxRetries: Number(card.querySelector(".phase-retries").value),
      retryFromPhaseId: card.querySelector(".phase-retryfrom").value.trim() || null,
      onExhaust: card.querySelector(".phase-exhaust").value
    };
  }

  function allowedSubActions() {
    const type = state.catalog.actionTypes.find(item => item.name === $("downstreamActionType").value);
    return type ? type.allowedSubActions : state.catalog.subActions;
  }

  async function loadParameterSets(actionKey) {
    state.parameterSets = await api(`/api/action-parameter-sets?actionKey=${encodeURIComponent(actionKey)}`);
    state.parameterSet = state.parameterSets[0] || null;
    renderParameterSets();
  }

  function renderParameterSets() {
    const items = state.parameterSets.map(item => ({ value: item.id, label: `${item.name} · r${item.revision}` }));
    fillSelect($("parameterSetSelect"), items, "不使用参数集");
    if (state.parameterSet) $("parameterSetSelect").value = state.parameterSet.id;
    showParameterSet();
  }

  function selectParameterSet() {
    state.parameterSet = state.parameterSets.find(item => item.id === $("parameterSetSelect").value) || null;
    showParameterSet(); invalidatePreview(); applyLocks();
  }

  function showParameterSet() {
    const value = state.parameterSet;
    $("parameterSetName").value = value ? value.name : "";
    $("fixtureMaterial").value = value ? [value.fixtureKey, value.materialKey].filter(Boolean).join(" / ") : "";
    $("parameterValues").value = pretty(value ? value.values : {});
  }

  async function saveParameterSet() {
    if (!state.current) return toast("请先保存 Action。");
    try {
      const pair = $("fixtureMaterial").value.split("/").map(value => value.trim());
      const body = { expectedRevision: state.parameterSet ? state.parameterSet.revision : null,
        actionKey: state.current.actionKey, name: $("parameterSetName").value.trim(),
        robotId: $("robotId").value.trim() || null, fixtureKey: pair[0] || null,
        materialKey: pair[1] || null, values: parseJson($("parameterValues").value, "联调参数"), enabled: true };
      const saved = state.parameterSet
        ? await api(`/api/action-parameter-sets/${state.parameterSet.id}`, { method: "PUT", body })
        : await api("/api/action-parameter-sets", { method: "POST", body });
      await loadParameterSets(state.current.actionKey);
      state.parameterSet = state.parameterSets.find(item => item.id === saved.id) || saved;
      renderParameterSets();
      toast("联调参数集已保存。");
    } catch (error) { report(error); }
  }

  function executionRequest(expectedPackageHash) {
    if (!state.current) throw new Error("请先选择 Action。");
    return { actionInstanceId: null, robotId: $("robotId").value.trim(), actionKey: state.current.actionKey,
      parameterSetId: state.parameterSet ? state.parameterSet.id : null,
      input: parseJson($("executionInput").value, "本次业务入参"),
      expectedPackageHash: expectedPackageHash || null, workflowInstanceId: null, workflowNodeInstanceId: null };
  }

  async function previewPackage() {
    try {
      state.preview = await api("/api/action-executions/preview", { method: "POST", body: executionRequest(null) });
      renderPreview(); toast("最终动作包已生成，请核对后执行。");
    } catch (error) { report(error); }
  }

  function renderPreview() {
    $("packagePreview").textContent = state.preview ? pretty(state.preview.commandInput) : "尚未生成预览";
    $("previewMeta").innerHTML = state.preview
      ? `<span class="badge">Action r${state.preview.actionRevision}</span><span class="badge">${escapeHtml(state.preview.downstreamActionType)}</span><span class="badge">Hash ${escapeHtml(state.preview.packageHash.slice(0, 12))}…</span>` : "";
    $("executeButton").disabled = !state.preview || !state.current
      || state.current.status !== "ACTIVE" || !ActionWorkbenchState.canEdit(state.workbench);
  }

  async function startExecution() {
    if (!state.preview) return;
    if (!window.confirm("执行期间 Action、参数集和入参将冻结；成功或物理结果明确的失败后自动解冻，结果未知时继续冻结。确认下发？")) return;
    try {
      const request = executionRequest(state.preview.packageHash);
      request.actionInstanceId = window.crypto.randomUUID();
      state.execution = await api("/api/action-executions", { method: "POST", body: request });
      ActionWorkbenchState.lockForExecution(state.workbench, state.current.actionKey, state.execution.actionInstanceId);
      sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench));
      applyLocks(); renderExecution(); schedulePoll();
    } catch (error) { report(error); }
  }

  function newCommissioningTask() {
    if (state.workbench.executionLocked && !window.confirm("将新建一个联调任务。旧任务的快照与执行记录仍会保留，是否继续？")) return;
    clearTimeout(state.pollTimer);
    state.workbench = ActionWorkbenchState.newTask();
    state.execution = null; state.preview = null;
    sessionStorage.removeItem(TASK_KEY);
    renderExecution(); renderPreview();
    if (state.current) selectAction(state.current.actionKey).catch(report);
    else applyLocks();
  }

  function restoreExecutionIfNeeded(actionKey) {
    if (!state.workbench.executionId) { renderExecution(); return; }
    api(`/api/action-executions/${state.workbench.executionId}`).then(execution => {
      state.execution = execution;
      const released = releaseExecutionLockIfSettled();
      renderExecution();
      if (!released && state.workbench.executionLocked) schedulePoll();
    }).catch(report);
  }

  function schedulePoll() {
    clearTimeout(state.pollTimer);
    if (!state.execution) return;
    state.pollTimer = setTimeout(async () => {
      let released = false;
      try {
        state.execution = await api(`/api/action-executions/${state.execution.actionInstanceId}`);
        released = releaseExecutionLockIfSettled();
        renderExecution();
      }
      catch (error) { report(error); }
      if (!released && state.workbench.executionLocked) schedulePoll();
    }, 1200);
  }

  function releaseExecutionLockIfSettled() {
    const released = ActionWorkbenchState.releaseAfterSettled(state.workbench, state.execution);
    if (!released) return false;
    sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench));
    applyLocks();
    toast(state.execution.state === "PHYSICAL_DONE" || state.execution.state === "COMPLETED"
      ? "联调执行成功，页面已自动解冻。"
      : "联调执行已结束，页面已自动解冻，请根据失败原因调参。");
    return true;
  }

  function renderExecution() {
    if (!state.execution) {
      $("executionSummary").textContent = "当前联调任务尚未执行"; $("stepTimeline").innerHTML = ""; return;
    }
    const error = state.execution.error || {};
    const errorMessage = error.message || error.detail && error.detail.message;
    $("executionSummary").innerHTML = `<strong>${escapeHtml(state.execution.state)}</strong> · ${escapeHtml(state.execution.actionInstanceId)} · 物理结果${state.execution.physicalResultKnown ? "已确认" : "未确认"}`
      + (errorMessage ? `<div class="execution-error"><b>失败原因：</b>${escapeHtml(errorMessage)}</div>` : "");
    const steps = Array.isArray(state.execution.resolvedSteps) ? state.execution.resolvedSteps
      : state.preview && Array.isArray(state.preview.resolvedSteps) ? state.preview.resolvedSteps : [];
    $("stepTimeline").innerHTML = steps.map((step, index) => {
      const stepState = String(step.state || "待执行");
      const failed = stepState.includes("FAILED") || stepState === "ERROR";
      return `<div class="timeline-step ${failed ? "failed" : ""}"><b>${index + 1}</b><span>${escapeHtml(step.phaseId || step.subAction || "步骤")}</span><small>${escapeHtml(stepState)}</small></div>`;
    }).join("");
  }

  function applyLocks() {
    const canEdit = ActionWorkbenchState.canEdit(state.workbench, state.current && state.current.actionKey);
    document.querySelectorAll("[data-editable],[data-task-editable]").forEach(element => { element.disabled = !canEdit; });
    $("actionSelect").disabled = state.workbench.executionLocked;
    $("newActionButton").disabled = !canEdit;
    $("saveActionButton").disabled = !canEdit;
    $("activateButton").disabled = !canEdit || !state.current;
    $("newTaskButton").disabled = false;
    $("lockBanner").hidden = canEdit;
    if (state.current) $("actionKey").disabled = true;
    renderPreview();
  }

  function invalidatePreview() {
    if (!state.preview) return;
    state.preview = null; renderPreview();
  }

  async function api(url, options) {
    const request = Object.assign({ headers: { "Content-Type": "application/json" } }, options || {});
    if (request.body && typeof request.body !== "string") request.body = JSON.stringify(request.body);
    const response = await fetch(url, request);
    if (response.status === 204) return null;
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("json") ? await response.json() : await response.text();
    if (!response.ok) throw new Error(typeof body === "string" ? body : body.message || body.error || `HTTP ${response.status}`);
    return body;
  }

  function fillSelect(select, values, emptyLabel) {
    const items = values.map(value => typeof value === "string" ? { value, label: value } : value);
    select.innerHTML = (emptyLabel ? `<option value="">${escapeHtml(emptyLabel)}</option>` : "")
      + items.map(item => `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`).join("");
  }
  function optionsHtml(values, selected) { return values.map(value => `<option ${value === selected ? "selected" : ""}>${escapeHtml(value)}</option>`).join(""); }
  function parseJson(value, label) { try { const result = JSON.parse(value || "{}"); if (!result || Array.isArray(result) || typeof result !== "object") throw new Error(); return result; } catch (_) { throw new Error(`${label} 必须是合法的 JSON 对象。`); } }
  function pretty(value) { return JSON.stringify(value == null ? {} : value, null, 2); }
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  function escapeHtml(value) { return String(value == null ? "" : value).replace(/[&<>"']/g, char => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", "\"":"&quot;", "'":"&#39;" }[char])); }
  function readSessionTask() { try { return JSON.parse(sessionStorage.getItem(TASK_KEY) || "null"); } catch (_) { return null; } }
  function toast(message) { const node = $("toast"); node.textContent = message; node.classList.add("show"); setTimeout(() => node.classList.remove("show"), 3200); }
  function report(error) { console.error(error); toast(error.message || String(error)); }
})();

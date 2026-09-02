(function () {
  "use strict";

  const $ = id => document.getElementById(id);
  const TASK_KEY = "kunling.action.currentExecution";
  const FALLBACK_OPERATIONS = [
    "MOVE_TO_MAP_POINT", "MOVE_TO_POSE", "GRIP", "GRIP.OPEN", "GRIP.CLOSE",
    "GRIP.VERIFY_LOAD", "GRIP_OPEN", "GRIP_CLOSE", "GRIP_VERIFY_LOAD",
    "VISION.VERIFY_MATERIAL", "VISION.VERIFY_PLACEMENT", "VISION.CAPTURE",
    "CHASSIS_VERIFY_STOPPED", "ARM_VERIFY_HOME"
  ];
  const state = {
    actions: [], robots: [],
    catalog: { operationSuggestions: FALLBACK_OPERATIONS, parameterExamples: {} },
    current: null, draft: emptyDefinition(), stepUi: [], stepJsonErrors: new Map(),
    dirty: false, drawerSyncedText: "", preview: null, execution: null, executionEvents: [],
    workbench: ActionWorkbenchState.create(readSessionTask()), pollTimer: null
  };

  document.addEventListener("DOMContentLoaded", initialise);

  async function initialise() {
    bindEvents();
    await loadCatalog();
    const results = await Promise.allSettled([loadRobots(), loadActions()]);
    results.filter(result => result.status === "rejected").forEach(result => report(result.reason));
    renderConnectionState();
    applyLocks();
  }

  function bindEvents() {
    $("refreshButton").addEventListener("click", refreshData);
    $("actionSelect").addEventListener("change", changeActionSelection);
    $("robotSelect").addEventListener("change", () => {
      clearRelativeStates();
      invalidatePreview();
      renderSteps();
      applyLocks();
    });
    $("actionName").addEventListener("input", event => updateHeader("name", event.target.value));
    $("timeoutMs").addEventListener("input", event => updateHeader("timeoutMs", Number(event.target.value)));
    $("newActionButton").addEventListener("click", () => newAction());
    $("saveActionButton").addEventListener("click", saveAction);
    $("enableButton").addEventListener("click", toggleEnabled);
    $("addStepButton").addEventListener("click", () => addStep());
    $("openJsonDrawerButton").addEventListener("click", openJsonDrawer);
    $("closeJsonDrawerButton").addEventListener("click", closeJsonDrawer);
    $("jsonDrawerBackdrop").addEventListener("click", event => {
      if (event.target === $("jsonDrawerBackdrop")) closeJsonDrawer();
    });
    $("syncJsonButton").addEventListener("click", syncDefinitionJson);
    $("applyJsonButton").addEventListener("click", applyDefinitionJson);
    $("previewButton").addEventListener("click", previewPackage);
    $("executeButton").addEventListener("click", startExecution);
    document.addEventListener("keydown", event => {
      if (event.key === "Escape" && !$("jsonDrawerBackdrop").hidden) closeJsonDrawer();
    });
    window.addEventListener("beforeunload", event => {
      if (!state.dirty) return;
      event.preventDefault();
      event.returnValue = "";
    });
  }

  function updateHeader(field, value) {
    if (!canEdit()) return;
    state.draft[field] = value;
    markDirty();
  }

  async function loadCatalog() {
    try {
      const catalog = await api("/api/action-protocol/catalog");
      if (catalog && Array.isArray(catalog.operationSuggestions)) state.catalog = catalog;
    } catch (error) {
      console.warn("Action 协议目录读取失败，使用页面内置清单。", error);
    }
  }

  async function refreshData() {
    if (state.dirty && !window.confirm("当前存在未保存修改，确认放弃并刷新？")) return;
    const actionId = state.current && state.current.definition.id;
    const robotId = $("robotSelect").value;
    try {
      await loadCatalog();
      await Promise.all([loadRobots(robotId), loadActions(actionId)]);
      toast("工作台数据已刷新。");
    } catch (error) { report(error); }
  }

  async function changeActionSelection() {
    const targetId = $("actionSelect").value;
    const currentId = state.current && state.current.definition.id || "";
    if (state.dirty && targetId !== currentId
        && !window.confirm("当前存在未保存修改，确认放弃并切换 Action？")) {
      $("actionSelect").value = currentId;
      return;
    }
    if (targetId) await selectAction(targetId);
    else newAction();
  }

  async function loadRobots(preferredRobotId) {
    const robots = await api("/api/robots");
    state.robots = Array.isArray(robots) ? robots : [];
    fillSelect($("robotSelect"), state.robots.map(robot => ({
      value: robot.robotId,
      label: `${robot.robotId} · ${robot.robotType || "ROBOT"} · ${operationCount(robot)}项能力`
    })), "当前没有在线机器人", preferredRobotId);
    renderConnectionState();
  }

  async function loadActions(preferredId) {
    const actions = await api("/api/actions");
    state.actions = Array.isArray(actions) ? actions : [];
    fillSelect($("actionSelect"), state.actions.map(item => ({
      value: item.definition.id,
      label: `${item.definition.name} · ${item.definition.enabled ? "已启用" : "未启用"}`
    })), "新建或选择 Action", preferredId);
    const id = preferredId || state.workbench.actionDefinitionId
      || state.current && state.current.definition.id
      || state.actions[0] && state.actions[0].definition.id;
    if (id && state.actions.some(item => item.definition.id === id)) await selectAction(id);
    else newAction(true);
  }

  async function selectAction(id) {
    try {
      state.current = await api(`/api/actions/${encodeURIComponent(id)}`);
      state.draft = clone(state.current.definition);
      resetStepUi();
      syncHeaderInputs();
      $("actionSelect").value = state.draft.id;
      state.preview = null;
      state.execution = null;
      state.executionEvents = [];
      state.dirty = false;
      state.workbench.serverLocked = Boolean(state.current.executionLocked);
      if (state.current.executionLocked && state.current.activeExecutionId) {
        state.workbench.actionDefinitionId = state.draft.id;
        state.workbench.executionId = state.current.activeExecutionId;
        state.workbench.executionLocked = true;
        persistWorkbench();
      }
      renderDefinitionState();
      renderSteps();
      renderPreview();
      renderExecution();
      applyLocks();
      restoreExecutionIfNeeded();
    } catch (error) { report(error); }
  }

  function newAction(silent) {
    if (!canEdit()) return !silent && toast("当前 Action 正在执行，不能新建定义。", true);
    if (!silent && state.dirty && !window.confirm("当前存在未保存修改，确认放弃并新建 Action？")) return;
    state.current = null;
    state.draft = emptyDefinition();
    state.workbench.serverLocked = false;
    state.stepUi = [];
    state.stepJsonErrors.clear();
    $("actionSelect").value = "";
    syncHeaderInputs();
    state.preview = null;
    state.execution = null;
    state.executionEvents = [];
    state.dirty = true;
    renderDefinitionState();
    renderSteps();
    renderPreview();
    renderExecution();
    applyLocks();
  }

  async function saveAction() {
    try {
      const definition = ActionDraftModel.snapshotForSave(state.draft);
      const existingId = state.current && state.current.definition.id;
      state.current = existingId
        ? await api(`/api/actions/${encodeURIComponent(existingId)}`, { method: "PUT", body: definition })
        : await api("/api/actions", { method: "POST", body: definition });
      state.dirty = false;
      toast("Action 定义已保存。");
      await loadActions(state.current.definition.id);
    } catch (error) { report(error); }
  }

  async function toggleEnabled() {
    if (!state.current) return toast("请先保存 Action。", true);
    if (state.dirty) return toast("存在未保存修改，请先保存。", true);
    try {
      const definition = state.current.definition;
      const suffix = definition.enabled ? "disable" : `enable?robotId=${encodeURIComponent(selectedRobotId())}`;
      state.current = await api(`/api/actions/${encodeURIComponent(definition.id)}/${suffix}`, { method: "POST" });
      toast(definition.enabled ? "Action 已停用。" : "Action 已启用。");
      await loadActions(state.current.definition.id);
    } catch (error) { report(error); }
  }

  function addStep(source) {
    if (!canEdit()) return;
    const index = state.draft.steps.length + 1;
    const operation = availableOperations()[0] || "MOVE_TO_POSE";
    state.draft.steps.push(source ? clone(source) : {
      stepId: `step-${String(index).padStart(2, "0")}`,
      operation, params: parameterExample(operation), gate: true, onFailure: stopAndReportPolicy()
    });
    state.stepUi.push(createStepUi());
    renderSteps();
    markDirty();
  }

  function renderSteps() {
    const list = $("stepList");
    list.innerHTML = "";
    ensureStepUi();
    if (!state.draft.steps.length) {
      list.innerHTML = '<div class="step-empty"><div><b>尚未编排子动作</b><span>添加步骤后，页面会按列表顺序生成串行动作包。</span></div></div>';
      renderCommandWarnings();
      applyLocks();
      return;
    }
    const operations = availableOperations();
    state.draft.steps.forEach((step, index) => renderStep(list, step, state.stepUi[index], index, operations));
    renderCommandWarnings();
    applyLocks();
  }

  function renderStep(list, step, ui, index, operations) {
    const options = Array.from(new Set([step.operation].concat(operations))).filter(Boolean)
      .map(value => `<option value="${escapeHtml(value)}" ${value === step.operation ? "selected" : ""}>${escapeHtml(value)}</option>`).join("");
    const card = document.createElement("article");
    card.className = "step";
    card.dataset.index = index;
    card.style.animationDelay = `${Math.min(index * 35, 210)}ms`;
    card.innerHTML = `<div class="step-head">
      <span class="step-index">${String(index + 1).padStart(2, "0")}</span>
      <strong class="step-title">${escapeHtml(step.operation || "未选择操作")}</strong>
      <div class="step-tools"><button type="button" data-op="up" title="上移">↑</button><button type="button" data-op="down" title="下移">↓</button><button type="button" data-op="copy">复制</button><button type="button" data-op="delete">删除</button></div>
    </div><div class="step-grid">
      <label class="step-field"><span>STEP ID</span><input class="step-id" value="${escapeHtml(step.stepId || "")}" data-editable></label>
      <label class="step-field"><span>原子操作 OPERATION</span><select class="step-operation" data-editable>${options}</select></label>
      <label class="gate-switch"><input class="step-gate" type="checkbox" ${step.gate ? "checked" : ""} data-editable>门禁步骤 GATE</label>
    </div><div class="parameter-heading"><div><b>固定参数 PARAMETERS</b><small>未知 operation 也按 JSON 类型生成通用表单</small></div>
      <div class="parameter-tabs"><button type="button" data-mode="form" class="${ui.mode === "form" ? "active" : ""}">表单</button><button type="button" data-mode="json" class="${ui.mode === "json" ? "active" : ""}">参数 JSON</button></div>
    </div><div class="parameter-host"></div>
    <details class="failure-policy"><summary>失败策略 ON FAILURE</summary><textarea class="step-failure" spellcheck="false" data-editable>${escapeHtml(pretty(step.onFailure || stopAndReportPolicy()))}</textarea></details>`;
    card.querySelector(".step-id").addEventListener("input", event => { step.stepId = event.target.value; markDirty(); });
    card.querySelector(".step-operation").addEventListener("change", event => changeStepOperation(index, event.target.value));
    card.querySelector(".step-gate").addEventListener("change", event => { step.gate = event.target.checked; markDirty(); });
    card.querySelector(".step-tools").addEventListener("click", event => handleStepOperation(event, index));
    card.querySelector(".parameter-tabs").addEventListener("click", event => {
      if (event.target.dataset.mode) switchParameterMode(index, event.target.dataset.mode);
    });
    card.querySelector(".step-failure").addEventListener("change", event => applyFailureJson(event, step, ui, index));
    list.appendChild(card);
    renderParameterArea(card.querySelector(".parameter-host"), step, ui, index);
  }

  function applyFailureJson(event, step, ui, index) {
    try {
      step.onFailure = parseJsonObject(event.target.value, `步骤 ${index + 1} 失败策略`);
      state.stepJsonErrors.delete(`${ui.key}:failure`);
      markDirty();
    } catch (error) {
      state.stepJsonErrors.set(`${ui.key}:failure`, error.message);
      report(error);
    }
  }

  function renderParameterArea(host, step, ui, index) {
    if (ui.mode === "json") return renderParameterJson(host, step, ui, index);
    ActionParameterPresenters.render(host, step.operation, step.params || {}, {
      editable: canEdit(), relativeState: ui.relative,
      canProbe: Boolean(selectedRobot()) && hasMoveToPoseCapability(selectedRobot()),
      probeDisabledReason: probeDisabledReason(), onError: report,
      onChange: parameters => { step.params = parameters; ui.parameterJsonText = null; markDirty(); },
      onTransientChange: () => renderSteps(), onProbe: () => probeArmPosition(index),
      onAppliedTarget: () => toast("计算结果已写入绝对目标字段，保存后才会持久化。")
    });
  }

  function renderParameterJson(host, step, ui, index) {
    if (ui.parameterJsonText == null) ui.parameterJsonText = pretty(step.params || {});
    host.innerHTML = `<div class="parameter-json-editor"><textarea spellcheck="false" ${canEdit() ? "" : "disabled"}>${escapeHtml(ui.parameterJsonText)}</textarea>
      <div><span>${ui.parameterJsonDirty ? "存在未应用的 JSON 修改" : "JSON 与当前草稿一致"}</span><button type="button" class="button button-outline" ${canEdit() ? "" : "disabled"}>应用参数 JSON</button></div></div>`;
    const textarea = host.querySelector("textarea");
    textarea.addEventListener("input", () => {
      ui.parameterJsonText = textarea.value;
      ui.parameterJsonDirty = textarea.value !== pretty(step.params || {});
      state.stepJsonErrors.set(`${ui.key}:params-pending`, `步骤 ${index + 1} 存在未应用的参数 JSON。`);
      host.querySelector("span").textContent = "存在未应用的 JSON 修改";
    });
    host.querySelector("button").addEventListener("click", () => {
      try {
        step.params = parseJsonObject(textarea.value, `步骤 ${index + 1} 参数`);
        ui.parameterJsonText = pretty(step.params);
        ui.parameterJsonDirty = false;
        state.stepJsonErrors.delete(`${ui.key}:params-pending`);
        state.stepJsonErrors.delete(`${ui.key}:params-invalid`);
        clearRelativeState(ui);
        markDirty();
        renderSteps();
        toast("参数 JSON 已应用到当前步骤。");
      } catch (error) {
        state.stepJsonErrors.set(`${ui.key}:params-invalid`, error.message);
        report(error);
      }
    });
  }

  async function probeArmPosition(index) {
    try {
      const robotId = selectedRobotId();
      const result = await api(`/api/action-debug/robots/${encodeURIComponent(robotId)}/arm-position`, { method: "POST" });
      if (!state.stepUi[index]) return;
      state.stepUi[index].relative.probe = ActionRelativeMotion.normalizeProbe(robotId, result);
      toast("机械臂当前位置已更新；未触发任何移动。");
    } catch (error) { throw error; }
  }

  function switchParameterMode(index, mode) {
    const ui = state.stepUi[index];
    if (!ui || ui.mode === mode) return;
    if (ui.mode === "json" && ui.parameterJsonDirty
        && !window.confirm("参数 JSON 存在未应用修改，确认放弃并返回表单？")) return;
    if (mode === "json") ui.parameterJsonText = pretty(state.draft.steps[index].params || {});
    else {
      ui.parameterJsonText = null;
      ui.parameterJsonDirty = false;
      state.stepJsonErrors.delete(`${ui.key}:params-pending`);
      state.stepJsonErrors.delete(`${ui.key}:params-invalid`);
    }
    ui.mode = mode;
    renderSteps();
  }

  function changeStepOperation(index, operation) {
    const step = state.draft.steps[index];
    if (!step || step.operation === operation) return;
    const example = parameterExample(operation);
    const hasExample = Object.keys(example).length > 0;
    const hasCurrent = step.params && Object.keys(step.params).length > 0;
    let loadExample = hasExample && !hasCurrent;
    if (hasExample && hasCurrent) {
      loadExample = window.confirm(`已切换到 ${operation}。\n\n确定：载入协议范例\n取消：保留当前参数`);
    }
    step.operation = operation;
    if (loadExample) step.params = example;
    const ui = state.stepUi[index];
    ui.mode = "form";
    ui.parameterJsonText = null;
    ui.parameterJsonDirty = false;
    clearRelativeState(ui);
    markDirty();
    renderSteps();
  }

  function handleStepOperation(event, index) {
    const operation = event.target.dataset.op;
    if (!operation || !canEdit()) return;
    if (operation === "delete") {
      clearUiErrors(state.stepUi[index]);
      state.stepUi.splice(index, 1);
      state.draft.steps.splice(index, 1);
    } else if (operation === "copy") {
      const copy = clone(state.draft.steps[index]);
      copy.stepId = nextCopyStepId(copy.stepId, state.draft.steps);
      state.draft.steps.splice(index + 1, 0, copy);
      state.stepUi.splice(index + 1, 0, createStepUi());
    } else if (operation === "up" && index > 0) swapSteps(index, index - 1);
    else if (operation === "down" && index < state.draft.steps.length - 1) swapSteps(index, index + 1);
    renderSteps();
    markDirty();
  }

  function swapSteps(left, right) {
    [state.draft.steps[left], state.draft.steps[right]] = [state.draft.steps[right], state.draft.steps[left]];
    [state.stepUi[left], state.stepUi[right]] = [state.stepUi[right], state.stepUi[left]];
  }

  function openJsonDrawer() {
    try {
      syncDefinitionJson();
      $("jsonDrawerBackdrop").hidden = false;
      document.body.classList.add("drawer-open");
      const editor = $("definitionJson");
      editor.focus();
      editor.setSelectionRange(0, 0);
      editor.scrollTop = 0;
    } catch (error) { report(error); }
  }

  function closeJsonDrawer() {
    if ($("jsonDrawerBackdrop").hidden) return;
    if ($("definitionJson").value !== state.drawerSyncedText
        && !window.confirm("完整 JSON 存在未应用修改，确认关闭并放弃？")) return;
    $("jsonDrawerBackdrop").hidden = true;
    document.body.classList.remove("drawer-open");
    $("openJsonDrawerButton").focus();
  }

  function syncDefinitionJson() {
    const text = pretty(state.draft);
    $("definitionJson").value = text;
    state.drawerSyncedText = text;
  }

  function applyDefinitionJson() {
    try {
      const definition = ActionDraftModel.applyJson($("definitionJson").value,
        state.current && state.current.definition);
      state.draft = clone(definition);
      resetStepUi();
      syncHeaderInputs();
      renderDefinitionState();
      renderSteps();
      markDirty();
      state.drawerSyncedText = $("definitionJson").value;
      toast("完整 JSON 已覆盖页面草稿，保存后才会写入服务端。");
    } catch (error) { report(error); }
  }

  async function previewPackage() {
    if (!state.current) return toast("请先保存 Action。", true);
    if (state.dirty) return toast("存在未保存修改，请先保存再预览。", true);
    try {
      state.preview = await api("/api/action-executions/preview", { method: "POST",
        body: { actionDefinitionId: state.current.definition.id, robotId: selectedRobotId() } });
      renderPreview();
      applyLocks();
      toast("动作包预览已生成，尚未下发设备。");
    } catch (error) { report(error); }
  }

  function renderPreview() {
    $("packagePreview").textContent = state.preview ? pretty(state.preview) : "尚未生成预览";
    $("previewMeta").innerHTML = state.preview
      ? `<span>HASH ${escapeHtml(shortHash(state.preview.packageHash))}</span><span>TIMEOUT ${escapeHtml(state.preview.timeoutMs)}ms</span>` : "";
  }

  async function startExecution() {
    if (!state.preview || !state.current) return;
    const robotId = selectedRobotId();
    if (!window.confirm(`即将把 ${state.current.definition.name} 下发到机器人 ${robotId}。\n\n该操作可能触发真实设备运动，确认继续？`)) return;
    try {
      const receipt = await api("/api/action-executions", { method: "POST", body: {
        actionInstanceId: createUuid(), actionDefinitionId: state.current.definition.id, robotId
      } });
      state.execution = await api(`/api/action-executions/${encodeURIComponent(receipt.actionInstanceId)}`);
      state.executionEvents = [];
      ActionWorkbenchState.lockForExecution(state.workbench, state.current.definition.id, receipt.actionInstanceId);
      persistWorkbench();
      renderSteps();
      renderExecution();
      schedulePoll();
      toast("动作包已受理，正在等待下游执行事实。");
    } catch (error) { report(error); }
  }

  function restoreExecutionIfNeeded() {
    if (!state.workbench.executionId || state.workbench.actionDefinitionId !== (state.current && state.current.definition.id)) return renderExecution();
    refreshExecution(state.workbench.executionId).then(() => {
      const released = releaseExecutionLockIfTerminal();
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
        await refreshExecution(state.execution.actionInstanceId);
        released = releaseExecutionLockIfTerminal();
        renderExecution();
      } catch (error) { report(error); }
      if (!released && state.workbench.executionLocked) schedulePoll();
    }, 1200);
  }

  async function refreshExecution(id) {
    const encoded = encodeURIComponent(id);
    [state.execution, state.executionEvents] = await Promise.all([
      api(`/api/action-executions/${encoded}`), api(`/api/action-executions/${encoded}/events?limit=500`)
    ]);
  }

  function releaseExecutionLockIfTerminal() {
    if (!ActionWorkbenchState.releaseAfterTerminal(state.workbench, state.execution)) return false;
    state.workbench.serverLocked = false;
    persistWorkbench();
    renderSteps();
    toast(state.execution.state === "UNKNOWN_HOLD"
      ? "Action 已进入 UNKNOWN_HOLD；定义锁已释放，现场仍需人工闭环。" : "Action 已结束，定义已解锁。");
    return true;
  }

  function renderExecution() {
    if (!state.execution) {
      $("executionSummary").className = "execution-empty";
      $("executionSummary").textContent = "当前没有执行记录";
      $("stepTimeline").innerHTML = "";
      return;
    }
    const error = state.execution.error || {};
    $("executionSummary").className = "execution-summary";
    $("executionSummary").innerHTML = `<strong>${escapeHtml(state.execution.state || "UNKNOWN")}</strong> · 物理结果 ${escapeHtml(state.execution.physicalOutcome || "UNKNOWN")}`
      + `<div class="execution-identities">actionInstanceId ${escapeHtml(state.execution.actionInstanceId)}<br>deviceCommandId ${escapeHtml(state.execution.deviceCommandId || "—")}</div>`
      + (error.message ? `<div class="execution-error"><b>失败原因：</b>${escapeHtml(error.message)}</div>` : "");
    $("stepTimeline").innerHTML = ActionExecutionTimeline.render(state.executionEvents,
      Array.isArray(state.execution.resolvedSteps) ? state.execution.resolvedSteps : [], state.execution.commandInput);
  }

  function renderDefinitionState() {
    const definition = state.current && state.current.definition;
    const enabled = Boolean(definition && definition.enabled);
    $("actionEnabled").textContent = enabled ? "已启用" : "未启用";
    $("actionEnabled").className = `status-tag ${enabled ? "status-enabled" : "status-draft"}`;
    $("actionId").textContent = definition ? `ID ${definition.id}` : "ID —";
    $("actionId").title = definition ? definition.id : "";
    $("enableButton").textContent = enabled ? "停用" : "启用";
  }

  function renderCommandWarnings() {
    const node = $("commandIdWarnings");
    const issues = ActionParameterEditor.commandIdIssues(state.draft);
    const hasCommandId = state.draft.steps.some(step => step.params && Object.prototype.hasOwnProperty.call(step.params, "commandId"));
    if (!issues.length && !hasCommandId) {
      node.hidden = true;
      node.innerHTML = "";
      return;
    }
    node.hidden = false;
    node.innerHTML = `<b>COMMAND ID 校验</b>${issues.map(issue => `<span>${escapeHtml(issue.message)}</span>`).join("")}`
      + (hasCommandId ? "<small>commandId 完全由 JSON 控制，服务端不替换；跨次执行复用可能触发下游幂等或冲突。</small>" : "");
  }

  function selectedRobot() { return state.robots.find(robot => robot.robotId === $("robotSelect").value) || null; }
  function selectedRobotId() {
    const robot = selectedRobot();
    if (!robot) throw new Error("必须选择在线机器人。");
    return robot.robotId;
  }
  function availableOperations() {
    const robot = selectedRobot();
    const registered = robot ? Object.keys(robot.operationCapabilities || {}) : [];
    return registered.length ? registered : (state.catalog.operationSuggestions || FALLBACK_OPERATIONS);
  }
  function parameterExample(operation) {
    const examples = state.catalog.parameterExamples || {};
    const result = clone(examples[operation] || examples[ActionParameterPresenters.normalizeOperation(operation)] || {});
    if (Object.prototype.hasOwnProperty.call(result, "commandId")) result.commandId = createCommandId();
    return result;
  }
  function hasMoveToPoseCapability(robot) {
    const capabilities = robot && robot.operationCapabilities || {};
    return Boolean(capabilities.MOVE_TO_POSE) || Object.values(capabilities).some(value => value && value.operation === "MOVE_TO_POSE");
  }
  function probeDisabledReason() {
    if (!selectedRobot()) return "机器人离线，无法获取当前位置";
    return hasMoveToPoseCapability(selectedRobot()) ? "只读查询，不会触发移动" : "机器人未注册 MOVE_TO_POSE 能力";
  }

  function canEdit() { return ActionWorkbenchState.canEdit(state.workbench); }
  function applyLocks() {
    const editable = canEdit();
    const robotOnline = Boolean(selectedRobot());
    const definition = state.current && state.current.definition;
    document.querySelectorAll("[data-editable]").forEach(element => { element.disabled = !editable; });
    $("actionSelect").disabled = state.workbench.executionLocked;
    $("robotSelect").disabled = state.workbench.executionLocked;
    $("newActionButton").disabled = !editable;
    $("saveActionButton").disabled = !editable;
    $("addStepButton").disabled = !editable;
    $("enableButton").disabled = !editable || !definition || state.dirty || (!definition.enabled && !robotOnline);
    $("previewButton").disabled = !editable || !definition || state.dirty || !robotOnline;
    $("executeButton").disabled = !editable || !state.preview || !definition || !definition.enabled || state.dirty || !robotOnline;
    $("definitionJson").readOnly = !editable;
    $("applyJsonButton").disabled = !editable;
    $("lockBanner").hidden = !state.workbench.executionLocked;
    $("saveActionButton").textContent = state.dirty ? "保存修改 *" : "保存 Action";
    renderConnectionState();
  }
  function renderConnectionState() {
    const robot = selectedRobot();
    $("connectionState").textContent = robot ? `机器人在线 · ${robot.robotId}` : "无在线机器人";
    $("connectionState").className = `signal ${robot ? "signal-online" : "signal-offline"}`;
    $("robotCapability").textContent = robot ? `${robot.robotId} 当前注册 ${operationCount(robot)} 项原子能力。`
      : "机器人离线不影响编辑；启用、预览和执行需要在线机器人。";
  }
  function markDirty() {
    state.dirty = true;
    invalidatePreview();
    renderCommandWarnings();
    applyLocks();
  }
  function invalidatePreview() {
    if (!state.preview) return;
    state.preview = null;
    renderPreview();
  }

  function resetStepUi() { state.stepUi = state.draft.steps.map(() => createStepUi()); state.stepJsonErrors.clear(); }
  function ensureStepUi() {
    while (state.stepUi.length < state.draft.steps.length) state.stepUi.push(createStepUi());
    if (state.stepUi.length > state.draft.steps.length) state.stepUi.length = state.draft.steps.length;
  }
  function createStepUi() {
    return { key: createUuid(), mode: "form", parameterJsonText: null, parameterJsonDirty: false,
      relative: ActionParameterPresenters.createRelativeState() };
  }
  function clearRelativeStates() { state.stepUi.forEach(clearRelativeState); }
  function clearRelativeState(ui) { if (ui) ui.relative = ActionParameterPresenters.createRelativeState(); }
  function clearUiErrors(ui) {
    if (!ui) return;
    Array.from(state.stepJsonErrors.keys()).filter(key => key.indexOf(`${ui.key}:`) === 0)
      .forEach(key => state.stepJsonErrors.delete(key));
  }
  function syncHeaderInputs() {
    $("actionName").value = state.draft.name == null ? "" : state.draft.name;
    $("timeoutMs").value = state.draft.timeoutMs == null ? 60000 : state.draft.timeoutMs;
  }

  async function api(url, options) { return ActionApi.request(window.fetch.bind(window), url, options); }
  function fillSelect(select, values, emptyLabel, preferredValue) {
    const previous = preferredValue || select.value;
    select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      + values.map(item => `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`).join("");
    if (previous && values.some(item => item.value === previous)) select.value = previous;
    else if (values.length) select.value = values[0].value;
  }
  function parseJsonObject(value, label) {
    try {
      const result = JSON.parse(value || "{}");
      if (!result || Array.isArray(result) || typeof result !== "object") throw new Error();
      return result;
    } catch (_) { throw new Error(`${label}必须是合法的 JSON 对象。`); }
  }
  function stopAndReportPolicy() {
    return { rules: [], defaultDirective: { action: "STOP_AND_REPORT", maxRetries: 0, delayMs: 0,
      verifyOperation: null, verifyParams: null, onExhaust: null } };
  }
  function emptyDefinition() { return { id: null, name: "", enabled: false, timeoutMs: 60000, steps: [] }; }
  function nextCopyStepId(source, steps) {
    const existing = new Set(steps.map(step => step.stepId));
    const base = `${source || "step"}-copy`;
    if (!existing.has(base)) return base;
    let suffix = 2;
    while (existing.has(`${base}-${suffix}`)) suffix += 1;
    return `${base}-${suffix}`;
  }
  function operationCount(robot) { return Object.keys(robot && robot.operationCapabilities || {}).length; }
  function shortHash(value) {
    const text = String(value || "");
    return text.length > 16 ? `${text.slice(0, 16)}…` : text || "—";
  }
  function createUuid() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, character => {
      const random = Math.random() * 16 | 0;
      return (character === "x" ? random : random & 0x3 | 0x8).toString(16);
    });
  }
  function createCommandId() { return createUuid().replace(/-/g, "").slice(0, 32); }
  function persistWorkbench() { sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench)); }
  function pretty(value) { return JSON.stringify(value, null, 2); }
  function clone(value) { return value === undefined ? undefined : JSON.parse(JSON.stringify(value)); }
  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, character => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
    })[character]);
  }
  function readSessionTask() {
    try { return JSON.parse(sessionStorage.getItem(TASK_KEY) || "null"); } catch (_) { return null; }
  }
  function toast(message, isError) {
    const node = $("toast");
    node.textContent = message;
    node.className = isError ? "show error" : "show";
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => { node.className = ""; }, 3600);
  }
  function report(error) {
    console.error(error);
    toast(error && error.message ? error.message : String(error), true);
  }
})();

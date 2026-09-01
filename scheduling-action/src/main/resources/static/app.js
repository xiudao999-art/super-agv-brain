(function () {
  "use strict";

  const $ = id => document.getElementById(id);
  const TASK_KEY = "kunling.action.currentExecution";
  const FALLBACK_OPERATIONS = [
    "MOVE_TO_MAP_POINT", "MOVE_TO_POSE", "GRIP.OPEN", "GRIP.CLOSE",
    "GRIP.VERIFY_LOAD", "VISION.VERIFY_MATERIAL", "VISION.VERIFY_PLACEMENT",
    "VISION.CAPTURE", "CHASSIS_VERIFY_STOPPED", "ARM_VERIFY_HOME"
  ];
  const state = {
    actions: [],
    robots: [],
    catalog: { operationSuggestions: FALLBACK_OPERATIONS },
    current: null,
    steps: [],
    dirty: false,
    preview: null,
    execution: null,
    executionEvents: [],
    workbench: ActionWorkbenchState.create(readSessionTask()),
    pollTimer: null
  };

  document.addEventListener("DOMContentLoaded", initialise);

  async function initialise() {
    bindEvents();
    await loadCatalog();
    const results = await Promise.allSettled([loadRobots(), loadActions()]);
    results.filter(result => result.status === "rejected")
      .forEach(result => report(result.reason));
    renderConnectionState();
    applyLocks();
  }

  function bindEvents() {
    $("refreshButton").addEventListener("click", refreshData);
    $("actionSelect").addEventListener("change", changeActionSelection);
    $("robotSelect").addEventListener("change", () => {
      invalidatePreview();
      renderSteps();
      renderConnectionState();
      applyLocks();
    });
    $("newActionButton").addEventListener("click", newAction);
    $("saveActionButton").addEventListener("click", saveAction);
    $("enableButton").addEventListener("click", toggleEnabled);
    $("addStepButton").addEventListener("click", () => addStep());
    $("syncJsonButton").addEventListener("click", syncDefinitionJson);
    $("applyJsonButton").addEventListener("click", applyDefinitionJson);
    $("previewButton").addEventListener("click", previewPackage);
    $("executeButton").addEventListener("click", startExecution);
    document.body.addEventListener("input", event => {
      if (event.target.matches("[data-editable]") && event.target.id !== "definitionJson") markDirty();
    });
    document.body.addEventListener("change", event => {
      if (event.target.matches("[data-editable]") && event.target.id !== "definitionJson") markDirty();
    });
  }

  async function loadCatalog() {
    try {
      const catalog = await api("/api/action-protocol/catalog");
      if (catalog && Array.isArray(catalog.operationSuggestions)) state.catalog = catalog;
    } catch (error) {
      console.warn("Action 协议目录读取失败，使用页面内置操作清单。", error);
    }
  }

  async function refreshData() {
    if (state.dirty && !window.confirm("当前存在未保存修改，确认放弃修改并刷新？")) return;
    const actionId = state.current && state.current.definition.id;
    const robotId = $("robotSelect").value;
    try {
      await loadCatalog();
      await Promise.all([loadRobots(robotId), loadActions(actionId)]);
      toast("工作台数据已刷新。");
    } catch (error) {
      report(error);
    }
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

    const id = preferredId
      || state.workbench.actionDefinitionId
      || state.current && state.current.definition.id
      || state.actions[0] && state.actions[0].definition.id;
    if (id && state.actions.some(item => item.definition.id === id)) await selectAction(id);
    else newAction(true);
  }

  async function selectAction(id) {
    if (!id) return;
    try {
      state.current = await api(`/api/actions/${encodeURIComponent(id)}`);
      const definition = state.current.definition;
      $("actionSelect").value = definition.id;
      $("actionName").value = definition.name;
      $("timeoutMs").value = definition.timeoutMs;
      state.steps = clone(definition.steps || []);
      state.preview = null;
      state.execution = null;
      state.executionEvents = [];
      state.dirty = false;
      state.workbench.serverLocked = Boolean(state.current.executionLocked);
      if (state.current.executionLocked && state.current.activeExecutionId) {
        state.workbench.actionDefinitionId = definition.id;
        state.workbench.executionId = state.current.activeExecutionId;
        state.workbench.executionLocked = true;
        persistWorkbench();
      }
      renderDefinitionState();
      renderSteps();
      writeDefinitionJson(definition);
      renderPreview();
      renderExecution();
      applyLocks();
      restoreExecutionIfNeeded();
    } catch (error) {
      report(error);
    }
  }

  function newAction(silent) {
    if (!canEdit()) {
      if (!silent) toast("当前 Action 正在执行，不能新建定义。", true);
      return;
    }
    if (!silent && state.dirty && !window.confirm("当前存在未保存修改，确认放弃并新建 Action？")) return;
    state.current = null;
    state.workbench.serverLocked = false;
    $("actionSelect").value = "";
    $("actionName").value = "";
    $("timeoutMs").value = "60000";
    state.steps = [];
    state.preview = null;
    state.execution = null;
    state.executionEvents = [];
    state.dirty = true;
    renderDefinitionState();
    renderSteps();
    writeDefinitionJson({ id: null, name: "", enabled: false, timeoutMs: 60000, steps: [] });
    renderPreview();
    renderExecution();
    applyLocks();
  }

  async function saveAction() {
    try {
      const definition = readDefinition();
      const existingId = state.current && state.current.definition.id;
      state.current = existingId
        ? await api(`/api/actions/${encodeURIComponent(existingId)}`, { method: "PUT", body: definition })
        : await api("/api/actions", { method: "POST", body: definition });
      state.dirty = false;
      toast("Action 定义已保存。");
      await loadActions(state.current.definition.id);
    } catch (error) {
      report(error);
    }
  }

  async function toggleEnabled() {
    if (!state.current) return toast("请先保存 Action。", true);
    if (state.dirty) return toast("存在未保存修改，请先保存再切换启用状态。", true);
    try {
      const definition = state.current.definition;
      const suffix = definition.enabled
        ? "disable"
        : `enable?robotId=${encodeURIComponent(selectedRobotId())}`;
      state.current = await api(`/api/actions/${encodeURIComponent(definition.id)}/${suffix}`, {
        method: "POST"
      });
      toast(definition.enabled ? "Action 已停用。" : "Action 已启用。");
      await loadActions(state.current.definition.id);
    } catch (error) {
      report(error);
    }
  }

  function readDefinition() {
    const currentDefinition = state.current && state.current.definition;
    const name = $("actionName").value.trim();
    const timeoutMs = Number($("timeoutMs").value);
    if (!name) throw new Error("Action 名称不能为空。");
    if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 3600000) {
      throw new Error("总超时必须是 1000-3600000 之间的整数。");
    }
    return {
      id: currentDefinition ? currentDefinition.id : null,
      name,
      enabled: currentDefinition ? currentDefinition.enabled : false,
      timeoutMs,
      steps: readSteps()
    };
  }

  function addStep(source) {
    if (!canEdit()) return;
    const index = state.steps.length + 1;
    const operation = availableOperations()[0] || "MOVE_TO_POSE";
    state.steps.push(source ? clone(source) : {
      stepId: `step-${String(index).padStart(2, "0")}`,
      operation,
      params: {},
      gate: true,
      onFailure: stopAndReportPolicy()
    });
    renderSteps();
    markDirty();
  }

  function renderSteps() {
    const list = $("stepList");
    list.innerHTML = "";
    if (!state.steps.length) {
      list.innerHTML = '<div class="step-empty"><div><b>尚未编排子动作</b><span>添加步骤后，页面会按列表顺序生成串行动作包。</span></div></div>';
      applyLocks();
      return;
    }

    const operations = availableOperations();
    state.steps.forEach((step, index) => {
      const options = Array.from(new Set([step.operation].concat(operations))).filter(Boolean)
        .map(value => `<option value="${escapeHtml(value)}" ${value === step.operation ? "selected" : ""}>${escapeHtml(value)}</option>`)
        .join("");
      const card = document.createElement("article");
      card.className = "step";
      card.dataset.index = index;
      card.style.animationDelay = `${Math.min(index * 35, 210)}ms`;
      card.innerHTML = `<div class="step-head">
          <span class="step-index">${String(index + 1).padStart(2, "0")}</span>
          <strong class="step-title">${escapeHtml(step.operation || "未选择操作")}</strong>
          <div class="step-tools" aria-label="步骤操作">
            <button type="button" data-op="up" title="上移">↑</button>
            <button type="button" data-op="down" title="下移">↓</button>
            <button type="button" data-op="copy">复制</button>
            <button type="button" data-op="delete">删除</button>
          </div>
        </div>
        <div class="step-grid">
          <label class="step-field"><span>STEP ID</span><input class="step-id" value="${escapeHtml(step.stepId || "")}" data-editable></label>
          <label class="step-field"><span>原子操作 OPERATION</span><select class="step-operation" data-editable>${options}</select></label>
          <label class="gate-switch"><input class="step-gate" type="checkbox" ${step.gate ? "checked" : ""} data-editable>门禁步骤 GATE</label>
        </div>
        <label class="step-field"><span>固定参数 PARAMS / JSON</span>
          <textarea class="step-params params-editor" spellcheck="false" data-editable>${escapeHtml(pretty(step.params || {}))}</textarea>
        </label>
        <details class="failure-policy">
          <summary>失败策略 ON FAILURE</summary>
          <textarea class="step-failure" spellcheck="false" data-editable>${escapeHtml(pretty(step.onFailure || stopAndReportPolicy()))}</textarea>
        </details>`;
      card.addEventListener("click", event => handleStepOperation(event, index));
      const operationSelect = card.querySelector(".step-operation");
      operationSelect.addEventListener("change", () => {
        card.querySelector(".step-title").textContent = operationSelect.value;
      });
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
      const copy = clone(state.steps[index]);
      copy.stepId = nextCopyStepId(copy.stepId, state.steps);
      state.steps.splice(index + 1, 0, copy);
    }
    if (operation === "up" && index > 0) {
      [state.steps[index - 1], state.steps[index]] = [state.steps[index], state.steps[index - 1]];
    }
    if (operation === "down" && index < state.steps.length - 1) {
      [state.steps[index + 1], state.steps[index]] = [state.steps[index], state.steps[index + 1]];
    }
    renderSteps();
    markDirty();
  }

  function readSteps() {
    return Array.from($("stepList").querySelectorAll(".step")).map(readStep);
  }

  function readStep(card, index) {
    const stepId = card.querySelector(".step-id").value.trim();
    if (!stepId) throw new Error(`步骤 ${index + 1} 的 stepId 不能为空。`);
    return {
      stepId,
      operation: card.querySelector(".step-operation").value,
      params: parseJsonObject(card.querySelector(".step-params").value, `步骤 ${index + 1} 参数`),
      gate: card.querySelector(".step-gate").checked,
      onFailure: parseJsonObject(card.querySelector(".step-failure").value, `步骤 ${index + 1} 失败策略`)
    };
  }

  function syncDefinitionJson() {
    try {
      writeDefinitionJson(readDefinition());
      toast("界面内容已同步到完整 JSON。");
    } catch (error) {
      report(error);
    }
  }

  function applyDefinitionJson() {
    try {
      const definition = parseJsonObject($("definitionJson").value, "完整 Action JSON");
      const currentId = state.current && state.current.definition.id;
      if (currentId && definition.id && definition.id !== currentId) {
        throw new Error("不能通过 JSON 修改已有 Action 的 id。");
      }
      if (!Array.isArray(definition.steps)) throw new Error("完整 Action JSON 的 steps 必须是数组。");
      $("actionName").value = definition.name || "";
      $("timeoutMs").value = definition.timeoutMs == null ? 60000 : definition.timeoutMs;
      state.steps = clone(definition.steps);
      renderSteps();
      markDirty();
      toast("完整 JSON 已应用到界面，保存后才会写入服务端。");
    } catch (error) {
      report(error);
    }
  }

  async function previewPackage() {
    if (!state.current) return toast("请先保存 Action。", true);
    if (state.dirty) return toast("存在未保存修改，请先保存再预览。", true);
    try {
      state.preview = await api("/api/action-executions/preview", {
        method: "POST",
        body: { actionDefinitionId: state.current.definition.id, robotId: selectedRobotId() }
      });
      renderPreview();
      applyLocks();
      toast("动作包预览已生成，尚未下发设备。");
    } catch (error) {
      report(error);
    }
  }

  function renderPreview() {
    $("packagePreview").textContent = state.preview ? pretty(state.preview) : "尚未生成预览";
    $("previewMeta").innerHTML = state.preview
      ? `<span>HASH ${escapeHtml(shortHash(state.preview.packageHash))}</span><span>TIMEOUT ${escapeHtml(state.preview.timeoutMs)}ms</span>`
      : "";
  }

  async function startExecution() {
    if (!state.preview || !state.current) return;
    const robotId = selectedRobotId();
    const name = state.current.definition.name;
    const confirmed = window.confirm(
      `即将把 ${name} 下发到机器人 ${robotId}。\n\n该操作可能触发真实设备运动，确认继续？`
    );
    if (!confirmed) return;
    try {
      const command = {
        actionInstanceId: createUuid(),
        actionDefinitionId: state.current.definition.id,
        robotId
      };
      const receipt = await api("/api/action-executions", { method: "POST", body: command });
      state.execution = await api(`/api/action-executions/${encodeURIComponent(receipt.actionInstanceId)}`);
      state.executionEvents = [];
      ActionWorkbenchState.lockForExecution(
        state.workbench,
        state.current.definition.id,
        receipt.actionInstanceId
      );
      persistWorkbench();
      applyLocks();
      renderExecution();
      schedulePoll();
      toast("动作包已受理，正在等待下游执行事实。");
    } catch (error) {
      report(error);
    }
  }

  function restoreExecutionIfNeeded() {
    if (!state.workbench.executionId
        || state.workbench.actionDefinitionId !== (state.current && state.current.definition.id)) {
      renderExecution();
      return;
    }
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
      } catch (error) {
        report(error);
      }
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
    state.workbench.serverLocked = false;
    persistWorkbench();
    applyLocks();
    toast(state.execution.state === "UNKNOWN_HOLD"
      ? "Action 已进入 UNKNOWN_HOLD；定义锁已释放，但现场仍需人工闭环。"
      : "Action 已结束，定义已解锁。");
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
    const steps = Array.isArray(state.execution.resolvedSteps) ? state.execution.resolvedSteps : [];
    $("stepTimeline").innerHTML = ActionExecutionTimeline.render(
      state.executionEvents,
      steps,
      state.execution.commandInput
    );
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
    const registered = robot ? Object.keys(robot.operationCapabilities || {}) : [];
    const suggestions = Array.isArray(state.catalog.operationSuggestions)
      ? state.catalog.operationSuggestions : FALLBACK_OPERATIONS;
    return registered.length ? registered : suggestions;
  }

  function canEdit() {
    return ActionWorkbenchState.canEdit(state.workbench);
  }

  function applyLocks() {
    const editable = canEdit();
    const robotOnline = Boolean(selectedRobot());
    const definition = state.current && state.current.definition;
    document.querySelectorAll("[data-editable]").forEach(element => {
      element.disabled = !editable;
    });
    $("actionSelect").disabled = state.workbench.executionLocked;
    $("robotSelect").disabled = state.workbench.executionLocked;
    $("newActionButton").disabled = !editable;
    $("saveActionButton").disabled = !editable;
    $("enableButton").disabled = !editable || !definition || state.dirty
      || (!definition.enabled && !robotOnline);
    $("previewButton").disabled = !editable || !definition || state.dirty || !robotOnline;
    $("executeButton").disabled = !editable || !state.preview || !definition
      || !definition.enabled || state.dirty || !robotOnline;
    $("lockBanner").hidden = !state.workbench.executionLocked;
    $("saveActionButton").textContent = state.dirty ? "保存修改 *" : "保存 Action";
    renderConnectionState();
  }

  function renderConnectionState() {
    const robot = selectedRobot();
    $("connectionState").textContent = robot ? `机器人在线 · ${robot.robotId}` : "无在线机器人";
    $("connectionState").className = `signal ${robot ? "signal-online" : "signal-offline"}`;
    $("robotCapability").textContent = robot
      ? `${robot.robotId} 当前注册 ${operationCount(robot)} 项原子能力。`
      : "机器人离线不影响编辑；启用、预览和执行需要在线机器人。";
  }

  function markDirty() {
    state.dirty = true;
    invalidatePreview();
    applyLocks();
  }

  function invalidatePreview() {
    if (!state.preview) return;
    state.preview = null;
    renderPreview();
  }

  async function api(url, options) {
    return ActionApi.request(window.fetch.bind(window), url, options);
  }

  function fillSelect(select, values, emptyLabel, preferredValue) {
    const previous = preferredValue || select.value;
    select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      + values.map(item => `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`).join("");
    if (previous && values.some(item => item.value === previous)) select.value = previous;
    else if (values.length) select.value = values[0].value;
  }

  function writeDefinitionJson(definition) {
    $("definitionJson").value = pretty(definition);
  }

  function parseJsonObject(value, label) {
    try {
      const result = JSON.parse(value || "{}");
      if (!result || Array.isArray(result) || typeof result !== "object") throw new Error();
      return result;
    } catch (_) {
      throw new Error(`${label} 必须是合法的 JSON 对象。`);
    }
  }

  function stopAndReportPolicy() {
    return {
      rules: [],
      defaultDirective: {
        action: "STOP_AND_REPORT",
        maxRetries: 0,
        delayMs: 0,
        verifyOperation: null,
        verifyParams: null,
        onExhaust: null
      }
    };
  }

  function nextCopyStepId(source, steps) {
    const existing = new Set(steps.map(step => step.stepId));
    const base = `${source || "step"}-copy`;
    if (!existing.has(base)) return base;
    let suffix = 2;
    while (existing.has(`${base}-${suffix}`)) suffix += 1;
    return `${base}-${suffix}`;
  }

  function operationCount(robot) {
    return Object.keys(robot && robot.operationCapabilities || {}).length;
  }

  function shortHash(value) {
    const text = String(value || "");
    return text.length > 16 ? `${text.slice(0, 16)}…` : text || "—";
  }

  function createUuid() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, character => {
      const random = Math.random() * 16 | 0;
      const value = character === "x" ? random : random & 0x3 | 0x8;
      return value.toString(16);
    });
  }

  function persistWorkbench() {
    sessionStorage.setItem(TASK_KEY, JSON.stringify(state.workbench));
  }

  function pretty(value) {
    return JSON.stringify(value == null ? {} : value, null, 2);
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, character => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
    })[character]);
  }

  function readSessionTask() {
    try {
      return JSON.parse(sessionStorage.getItem(TASK_KEY) || "null");
    } catch (_) {
      return null;
    }
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

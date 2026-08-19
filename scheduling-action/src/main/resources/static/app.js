const state = {
  capabilities: [], actionCatalog: [], drafts: [], releases: [], draft: null, definition: null,
  selectedPath: null, compile: null, dirty: false, previewOrigin: null, mainDraftId: null
};
const {
  getNodeAtPath,
  getNodeLocation,
  insertNodeAtLocation,
  moveNodeAtInsertion,
  removeNodeAtPath,
  replaceNodeAtPath
} = ActionOrdering;
const {
  createActionReference,
  getMissingRequiredBindings,
  listCatalogItems,
  parseBindingValue: parseReferenceBindingValue,
  promoteReferenceInput,
  updateReferenceBinding
} = ActionReference;
const {
  describeActionLifecycle,
  nextAvailablePatchVersion
} = ActionLifecycle;
const {
  catalogExclusionKey,
  isReleasePreview,
  listCompositeDrafts,
  listMainActionDrafts,
  preservePreviewOrigin
} = ActionPreview;
let workflowCanvas = null;
let pendingLifecycleAction = null;

const $ = selector => document.querySelector(selector);
const api = async (url, options = {}) => {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(problem.message || `请求失败 ${response.status}`);
  }
  return response.status === 204 ? null : response.json();
};

const escapeHtml = value => String(value ?? "").replace(/[&<>'"]/g, character => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
}[character]));
const clone = value => JSON.parse(JSON.stringify(value));
const notify = (message, error = false) => {
  const toast = $("#toast");
  toast.textContent = message;
  toast.className = `toast visible${error ? " error" : ""}`;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => toast.className = "toast", 3300);
};
const setStatus = message => $("#globalStatus").textContent = message;

async function initialize() {
  try {
    [state.capabilities, state.actionCatalog, state.drafts, state.releases] = await Promise.all([
      api("/api/capabilities"), api("/api/action-catalog"),
      api("/api/actions/drafts"), api("/api/actions/releases")
    ]);
    renderLibrary();
    renderDraftOptions();
    bindEvents();
    const mainActions = listMainActionDrafts(state.drafts);
    const editable = mainActions.find(item => item.status === "DRAFT") || mainActions[0];
    editable ? openDraft(editable.id) : createNewMainDefinition();
    setStatus("控制面已连接 · MySQL");
  } catch (error) {
    setStatus("控制面连接失败");
    notify(error.message, true);
  }
}

function bindEvents() {
  workflowCanvas = new ActionCanvas.WorkflowCanvas({
    canvas: $("#workflowCanvas"),
    viewport: $("#canvasViewport"),
    liveRegion: $("#canvasLiveRegion"),
    onSelect: selectNode,
    onMove: reorderNode,
    onExternalDrop: addCatalogAction,
    onNotice: notify
  });
  $("#draftSelect").addEventListener("change", changeDraftSelection);
  $("#leaveSecondaryWorkspaceButton").addEventListener("click", leaveSecondaryWorkspace);
  $("#newCompositeDraftButton").addEventListener("click", createNewCompositeDefinition);
  ["displayName", "description", "actionKey", "actionVersion", "scope"].forEach(id =>
    $(`#${id}`).addEventListener("input", event => {
      if (!state.definition) return;
      state.definition[id === "actionVersion" ? "version" : id] = event.target.value;
      markDirty();
      if (id === "actionKey" || id === "scope") renderActionCatalog();
      if (id === "actionKey") renderVersionOptions();
    }));
  $("#entryPoint").addEventListener("change", event => {
    state.definition.entryPoint = event.target.checked;
    renderActionType();
    renderDraftOptions();
    renderSecondaryWorkspaceBar();
    renderActionCatalog();
    markDirty();
  });
  $("#saveButton").addEventListener("click", saveDraft);
  $("#compileButton").addEventListener("click", compileDraft);
  $("#publishButton").addEventListener("click", publishDraft);
  $("#deleteButton").addEventListener("click", deleteSelected);
  $("#moveUpButton").addEventListener("click", () => moveSelected(-1));
  $("#moveDownButton").addEventListener("click", () => moveSelected(1));
  $("#cloneButton").addEventListener("click", cloneRelease);
  $("#lifecycleActionButton").addEventListener("click", openLifecycleDialog);
  $("#diffButton").addEventListener("click", compareVersions);
  $("#actionSearch").addEventListener("input", renderActionCatalog);
  document.querySelectorAll("[data-library-tab]").forEach(button =>
    button.addEventListener("click", () => setLibraryTab(button.dataset.libraryTab)));
  document.querySelectorAll("[data-kind]").forEach(button => button.addEventListener("click", () => addControlNode(button.dataset.kind)));
  document.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", () =>
    $(`#${button.dataset.closeDialog}`).close()));
  $("#versionForm").addEventListener("submit", createVersionDraft);
  $("#lifecycleForm").addEventListener("submit", executeLifecycleAction);
}

function renderLibrary() {
  $("#capabilityCount").textContent = state.capabilities.length;
  $("#capabilityLibrary").innerHTML = state.capabilities.map((capability, index) => `
    <button class="library-item ${capability.sideEffect === "PHYSICAL" ? "physical" : ""}" data-capability-index="${index}">
      <strong>${escapeHtml(capability.capabilityKey)}</strong>
      <small>${escapeHtml((capability.resources || []).join(" + ") || "system")}</small>
    </button>`).join("");
  document.querySelectorAll("[data-capability-index]").forEach(button =>
    button.addEventListener("click", () => addCapabilityNode(state.capabilities[Number(button.dataset.capabilityIndex)])));
  renderActionCatalog();
}

function renderActionCatalog() {
  renderCompositeDrafts();
  const currentActionKey = catalogExclusionKey(state.draft, state.definition);
  const catalog = listCatalogItems(state.actionCatalog, {
    scope: state.definition?.scope || "TIANJIN",
    query: $("#actionSearch")?.value || ""
  });
  $("#actionCount").textContent = catalog.length;
  $("#actionCatalogEmpty").classList.toggle("visible", catalog.length === 0);
  $("#actionCatalog").innerHTML = catalog.map((item, index) => {
    const requiredCount = Object.values(item.inputSchema || {}).filter(schema => schema.required).length;
    const viewingItem = isReleasePreview(state.draft) &&
      item.actionKey === state.definition?.actionKey && item.version === state.definition?.version;
    const sameActionSeries = !viewingItem && Boolean(currentActionKey) && item.actionKey === currentActionKey;
    const canReference = !isLocked() && !sameActionSeries;
    return `<article class="action-catalog-item ${item.hasPhysicalSideEffect ? "physical" : ""} ${viewingItem ? "viewing" : ""} ${sameActionSeries ? "current-series" : ""}" data-action-index="${index}" draggable="${canReference}">
      <span class="catalog-item-heading"><strong>${escapeHtml(item.displayName || item.actionKey)}</strong><b>${viewingItem ? "查看中" : sameActionSeries ? "当前系列" : "组合"}</b></span>
      <span class="catalog-identity">${escapeHtml(item.actionKey)} @ ${escapeHtml(item.version)}</span>
      <span class="catalog-meta"><i>${escapeHtml(item.scope)}</i><i>${requiredCount} 个输入</i><i>${item.atomicSteps?.length || 0} 个原子动作</i></span>
      <span class="catalog-actions"><button type="button" data-catalog-add="${index}" aria-label="引用 ${escapeHtml(item.displayName || item.actionKey)}" ${canReference ? "" : "disabled"}>${sameActionSeries ? "不可自引" : "＋ 引用"}</button><button type="button" data-catalog-view="${index}" aria-label="查看 ${escapeHtml(item.displayName || item.actionKey)}" ${viewingItem ? "disabled" : ""}>${viewingItem ? "查看中" : "查看"}</button><button type="button" data-catalog-version="${index}" aria-label="为 ${escapeHtml(item.displayName || item.actionKey)} 创建新版本">新版本</button></span>
    </article>`;
  }).join("");
  document.querySelectorAll(".action-catalog-item[data-action-index]").forEach(card => {
    const item = catalog[Number(card.dataset.actionIndex)];
    card.addEventListener("dragstart", event => {
      if (event.target.closest("button")) return event.preventDefault();
      if (isLocked() || item.actionKey === currentActionKey) return event.preventDefault();
      event.dataTransfer.effectAllowed = "copy";
      event.dataTransfer.setData(ActionCanvas.ACTION_CATALOG_MIME, JSON.stringify({ actionKey: item.actionKey, version: item.version }));
      card.classList.add("dragging");
    });
    card.addEventListener("dragend", () => card.classList.remove("dragging"));
  });
  document.querySelectorAll("[data-catalog-add]").forEach(button => button.addEventListener("click", () => {
    const item = catalog[Number(button.dataset.catalogAdd)];
    addCatalogAction({ actionKey: item.actionKey, version: item.version });
  }));
  document.querySelectorAll("[data-catalog-view]").forEach(button => button.addEventListener("click", () =>
    viewCatalogAction(catalog[Number(button.dataset.catalogView)])));
  document.querySelectorAll("[data-catalog-version]").forEach(button => button.addEventListener("click", () =>
    openVersionDialog(catalog[Number(button.dataset.catalogVersion)])));
}

function renderCompositeDrafts() {
  const drafts = listCompositeDrafts(state.drafts);
  $("#compositeDraftCount").textContent = drafts.length;
  $("#compositeDraftList").innerHTML = drafts.map(draft => {
    const editing = !isReleasePreview(state.draft) && state.draft?.id === draft.id;
    return `<article class="composite-draft-item ${editing ? "editing" : ""}">
      <span><strong>${escapeHtml(draft.definition.displayName || draft.actionKey || "未命名组合动作")}</strong><small>${escapeHtml(draft.actionKey || "未设置 Action Key")} @ ${escapeHtml(draft.definition.version)}</small></span>
      <button type="button" data-composite-draft-id="${escapeHtml(draft.id)}" ${editing ? "disabled" : ""}>${editing ? "编辑中" : "编辑草稿"}</button>
    </article>`;
  }).join("") || `<div class="composite-draft-empty">暂无组合动作草稿</div>`;
  document.querySelectorAll("[data-composite-draft-id]").forEach(button =>
    button.addEventListener("click", () => openCompositeDraft(button.dataset.compositeDraftId)));
}

async function openCompositeDraft(draftId) {
  if (!confirmDiscardChanges()) return;
  await openDraft(draftId);
  setLibraryTab("action");
}

function setLibraryTab(tab) {
  document.querySelectorAll("[data-library-tab]").forEach(button => {
    const active = button.dataset.libraryTab === tab;
    button.classList.toggle("active", active);
    button.setAttribute("aria-selected", String(active));
  });
  $("#capabilityPanel").hidden = tab !== "capability";
  $("#actionPanel").hidden = tab !== "action";
  $("#capabilityPanel").classList.toggle("active", tab === "capability");
  $("#actionPanel").classList.toggle("active", tab === "action");
}

function renderDraftOptions() {
  const mainActions = listMainActionDrafts(state.drafts).filter(draft =>
    draft.id !== state.draft?.id || state.definition?.entryPoint === true);
  $("#draftSelect").innerHTML = `<option value="NEW_MAIN">＋ 新建主 Action</option>` + mainActions.map(draft => {
    const release = state.releases.find(item => item.actionKey === draft.actionKey && item.actionVersion === draft.definition.version);
    const status = draft.status === "PUBLISHED" ? release?.status || draft.status : draft.status;
    return `<option value="${draft.id}">${escapeHtml(draft.actionKey)} @ ${escapeHtml(draft.definition.version)} · ${status}</option>`;
  }).join("");
  const currentMainId = state.definition?.entryPoint === true && state.draft?.id
    ? state.draft.id
    : state.mainDraftId;
  $("#draftSelect").value = mainActions.some(item => item.id === currentMainId)
    ? currentMainId
    : "NEW_MAIN";
}

function confirmDiscardChanges() {
  return !state.dirty || window.confirm("当前动作有未保存修改。继续将放弃这些修改，是否继续？");
}

function changeDraftSelection(event) {
  const targetId = event.target.value;
  if (!confirmDiscardChanges()) {
    renderDraftOptions();
    return;
  }
  targetId === "NEW_MAIN" ? createNewMainDefinition() : openDraft(targetId);
}

async function viewCatalogAction(catalogItem) {
  if (!confirmDiscardChanges()) return;
  const previewOrigin = preservePreviewOrigin(state.draft, state.previewOrigin);
  try {
    const release = await api(`/api/actions/releases/${encodeURIComponent(catalogItem.actionKey)}/${encodeURIComponent(catalogItem.version)}`);
    state.draft = {
      id: `RELEASE:${release.actionKey}@${release.actionVersion}`,
      actionKey: release.actionKey,
      revision: 0,
      definition: release.definition,
      status: "PUBLISHED",
      source: "RELEASE_VIEW"
    };
    state.definition = clone(release.definition);
    state.selectedPath = null;
    state.compile = null;
    state.dirty = false;
    state.previewOrigin = previewOrigin;
    renderDraftOptions();
    renderAll();
  } catch (error) { notify(`无法打开组合动作：${error.message}`, true); }
}

async function openDraft(id) {
  const origin = preservePreviewOrigin(state.draft, state.previewOrigin);
  try {
    state.draft = await api(`/api/actions/drafts/${id}`);
    state.definition = clone(state.draft.definition);
    state.selectedPath = null;
    state.compile = null;
    state.dirty = false;
    if (state.definition.entryPoint === true) {
      state.mainDraftId = state.draft.id;
      state.previewOrigin = null;
    } else {
      state.previewOrigin = origin;
    }
    renderDraftOptions();
    renderAll();
  } catch (error) { notify(error.message, true); }
}

function createNewMainDefinition() {
  startNewDefinition(true, null);
}

function createNewCompositeDefinition() {
  if (!confirmDiscardChanges()) return;
  const origin = preservePreviewOrigin(state.draft, state.previewOrigin);
  startNewDefinition(false, origin);
  setLibraryTab("action");
}

function startNewDefinition(entryPoint, origin) {
  state.draft = null;
  state.definition = ActionDefinition.createEmptyDefinition();
  state.definition.entryPoint = entryPoint;
  state.selectedPath = null;
  state.compile = null;
  state.dirty = true;
  state.previewOrigin = entryPoint ? null : origin;
  if (entryPoint) state.mainDraftId = null;
  renderDraftOptions();
  renderAll();
}

function renderAll() {
  const definition = state.definition;
  $("#displayName").value = definition.displayName || "";
  $("#description").value = definition.description || "";
  $("#actionKey").value = definition.actionKey || "";
  $("#actionVersion").value = definition.version || "";
  $("#scope").value = definition.scope || "TIANJIN";
  $("#entryPoint").checked = Boolean(definition.entryPoint);
  renderActionType();
  renderSecondaryWorkspaceBar();
  renderLibrary();
  renderCanvas();
  renderInspector();
  renderCompile();
  renderVersionOptions();
  updateLockState();
}

function renderSecondaryWorkspaceBar() {
  const previewing = isReleasePreview(state.draft);
  const editingComposite = state.definition?.entryPoint === false;
  const visible = previewing || editingComposite;
  const bar = $("#secondaryWorkspaceBar");
  bar.hidden = !visible;
  if (!visible) return;
  const creating = editingComposite && !state.draft;
  $("#secondaryWorkspaceMark").textContent = previewing ? "VIEW" : creating ? "NEW" : "EDIT";
  $("#secondaryWorkspaceTitle").textContent = previewing
    ? "正在只读查看全局组合动作"
    : creating ? "正在新建全局组合动作" : "正在编辑全局组合动作草稿";
  $("#secondaryWorkspaceDescription").textContent = previewing
    ? "这里展示的是独立发布版本，不会替换或挂载到当前主 Action。"
    : "组合动作属于全局资产；保存后仍不会进入主 Action 下拉框。";
  $("#secondaryWorkspaceIdentity").textContent =
    `${state.definition.displayName || state.definition.actionKey} · ${state.definition.actionKey}@${state.definition.version}`;
  const originDraft = state.drafts.find(item => item.id === state.previewOrigin?.draftId);
  const mainDraft = state.drafts.find(item => item.id === state.mainDraftId);
  $("#secondaryWorkspaceReturnLabel").textContent = originDraft
    ? `返回 ${originDraft.actionKey}`
    : mainDraft ? `返回 ${mainDraft.actionKey}` : "返回主 Action";
}

async function leaveSecondaryWorkspace() {
  if (!isReleasePreview(state.draft) && state.definition?.entryPoint !== false) return;
  if (!confirmDiscardChanges()) return;
  const returnDraftId = state.previewOrigin?.draftId || state.mainDraftId;
  if (returnDraftId && state.drafts.some(item => item.id === returnDraftId)) {
    await openDraft(returnDraftId);
    return;
  }
  createNewMainDefinition();
}

function renderActionType() {
  const type = ActionDefinition.describeActionType(Boolean(state.definition?.entryPoint));
  $("#actionTypeLabel").textContent = type.label;
  $("#actionTypeDescription").textContent = type.description;
}

function renderCanvas() {
  const steps = state.definition.steps || [];
  $("#nodeCount").textContent = `${countNodes(steps)} 个编译前节点`;
  $("#dirtyState").textContent = state.dirty ? "有未保存修改" : `草稿 r${state.draft?.revision || 0}`;
  $("#emptyCanvas").classList.toggle("visible", steps.length === 0);
  workflowCanvas?.render({
    definition: state.definition,
    capabilities: state.capabilities,
    actionCatalog: state.actionCatalog,
    selectedPath: state.selectedPath,
    locked: isLocked()
  });
  updateNodeToolbarState();
}

function selectNode(nodePath) {
  state.selectedPath = cloneNodePath(nodePath);
  renderCanvas();
  renderInspector();
}

function cloneNodePath(nodePath) {
  return Array.isArray(nodePath) ? nodePath.map(segment => ({ key: segment.key, index: segment.index })) : null;
}

function selectedNode() {
  if (!state.selectedPath) return null;
  try { return getNodeAtPath(state.definition, state.selectedPath); }
  catch { state.selectedPath = null; return null; }
}

function countNodes(nodes) {
  return (nodes || []).reduce((count, node) => count + 1 +
    countNodes(node.steps) + countNodes(node.then) + countNodes(node.else), 0);
}

function addCapabilityNode(capability) {
  if (isLocked()) return notify("已发布草稿不可编辑，请创建新版本。", true);
  const stepId = uniqueStepId(capability.capabilityKey.split(".").slice(-2).join("_"));
  const bindings = {};
  Object.entries(capability.inputSchema || {}).forEach(([name, schema]) => {
    if (schema.required) bindings[name] = defaultBinding(name, schema);
  });
  state.definition.steps.push({
    kind: "CAPABILITY", stepId, displayName: capability.capabilityKey, description: "", enabled: true,
    timeoutMs: capability.requiresMotionSafetyParameters ? 15000 : 5000,
    // 一期异常处置由工作流负责，Action 编辑器不配置自动重试或跳过。
    onFailure: { strategy: "ABORT", maxRetries: 0 },
    gate: Boolean(capability.safetyCritical), outputs: {}, capabilityKey: capability.capabilityKey, with: bindings
  });
  state.selectedPath = [{ key: "steps", index: state.definition.steps.length - 1 }];
  markDirty();
  renderCanvas(); renderInspector();
}

function addCatalogAction(identity, insertionLocation) {
  if (isLocked()) return notify("已发布草稿不可编辑，请创建新版本。", true);
  if (!state.definition) return;

  const catalogItem = state.actionCatalog.find(item =>
    item.actionKey === identity?.actionKey && item.version === identity?.version);
  if (!catalogItem) return notify("该发布动作已不在动作目录中，请刷新页面后重试。", true);
  if (catalogItem.actionKey === state.definition.actionKey) return notify("Action 不能直接引用自身。", true);
  if (catalogItem.scope !== state.definition.scope) return notify("只能引用与当前 Action 同一适用范围的动作。", true);

  try {
    const node = createActionReference(
      catalogItem,
      state.definition,
      flattenSteps(state.definition.steps).map(item => item.stepId));
    const location = insertionLocation || {
      parentPath: [],
      containerKey: "steps",
      insertionIndex: state.definition.steps.length
    };
    const result = insertNodeAtLocation(state.definition, location, node);
    state.definition = result.definition;
    state.selectedPath = result.nodePath;
    markDirty();
    renderCanvas();
    renderInspector();
    const missingCount = getMissingRequiredBindings(node, catalogItem).length;
    notify(missingCount
      ? `已引用 ${catalogItem.displayName || catalogItem.actionKey}@${catalogItem.version}，还有 ${missingCount} 个必填输入待配置。`
      : `已引用 ${catalogItem.displayName || catalogItem.actionKey}@${catalogItem.version}。`);
  } catch (error) {
    notify(`无法添加引用动作：${error.message}`, true);
  }
}

function defaultBinding(name, schema) {
  if (name === "pose") return { inlinePose: { frame: "BASE", unit: "MILLIMETER_DEGREE", x: 0, y: 0, z: 300, rx: 180, ry: 0, rz: 0 } };
  if (name === "station" && state.definition.inputSchema?.station) return "$input.station";
  if (name === "point" && state.definition.inputSchema?.point) return "$input.point";
  if (name === "speedProfile") return "COMMISSIONING_LOW";
  if (name === "collisionProfile") return "SAFE";
  if (name === "poseRole") return "CUSTOM";
  if (name === "cameraId") return "CAM01";
  if (name === "outputFormat") return "png";
  if (name === "simulatedPass") return true;
  if (name === "message") return "配置输入不满足动作约束";
  if (schema.enumValues?.length) return schema.enumValues[0];
  return { STRING: "", NUMBER: 0, INTEGER: 0, BOOLEAN: false, OBJECT: {}, ARRAY: [] }[schema.type];
}

function addControlNode(kind) {
  if (isLocked()) return notify("已发布草稿不可编辑，请创建新版本。", true);
  if (kind !== "FOREACH" && kind !== "CONDITION") return notify("不支持的结构节点。", true);
  const base = { stepId: uniqueStepId(kind.toLowerCase()), displayName: kind, description: "", enabled: true, timeoutMs: 5000, onFailure: { strategy: "ABORT", maxRetries: 0 }, gate: false, outputs: {} };
  const node = kind === "FOREACH"
    ? { kind, ...base, items: "$input.items", itemVariable: "$item", maxIterations: 6, orderBy: null, steps: [] }
    : { kind, ...base, condition: { operator: "IS_TRUE", left: "$input.enabled" }, then: [], else: [] };
  state.definition.steps.push(node);
  state.selectedPath = [{ key: "steps", index: state.definition.steps.length - 1 }];
  markDirty(); renderCanvas(); renderInspector();
}

function uniqueStepId(seed) {
  const safe = seed.replace(/[^a-zA-Z0-9_]/g, "_");
  const used = new Set(flattenSteps(state.definition.steps).map(item => item.stepId));
  let value = safe, suffix = 2;
  while (used.has(value)) value = `${safe}_${suffix++}`;
  return value;
}
function flattenSteps(nodes) { return (nodes || []).flatMap(node => [node, ...flattenSteps(node.steps), ...flattenSteps(node.then), ...flattenSteps(node.else)]); }

function renderInspector() {
  const node = selectedNode();
  $("#inspectorEmpty").style.display = node ? "none" : "block";
  let html = actionSchemaSection();
  if (node) {
    const detailSection = node.kind === "CAPABILITY"
      ? capabilitySection(node)
      : node.kind === "ACTION_REF"
        ? actionReferenceSection(node)
        : controlSection(node);
    html = commonNodeSection(node) + detailSection + actionSchemaSection();
  }
  $("#nodeInspector").innerHTML = html;
  bindInspector(node);
}

function commonNodeSection(node) {
  return `<section class="inspector-section"><h3>节点身份</h3><div class="form-grid">
    <label>Step ID<input data-node-field="stepId" value="${escapeHtml(node.stepId)}"></label>
    <label>显示名称<input data-node-field="displayName" value="${escapeHtml(node.displayName || "")}"></label>
    <label>超时 ms<input data-node-field="timeoutMs" type="number" value="${node.timeoutMs || 0}"></label>
    <label>Gate<select data-node-field="gate"><option value="false" ${!node.gate ? "selected" : ""}>否</option><option value="true" ${node.gate ? "selected" : ""}>是</option></select></label>
  </div></section>`;
}

function capabilitySection(node) {
  const capability = state.capabilities.find(item => item.capabilityKey === node.capabilityKey);
  if (!capability) return `<section class="inspector-section"><div class="risk-box">能力清单中不存在 ${escapeHtml(node.capabilityKey)}，Compiler 将拒绝发布。</div>${advancedNodeJson(node)}</section>`;
  const fields = Object.entries(capability.inputSchema || {}).map(([name, schema]) => bindingField(node, name, schema)).join("");
  const shortHash = capability.contractHash ? capability.contractHash.slice(0, 12) : "未同步";
  return `<section class="inspector-section"><h3>${escapeHtml(node.capabilityKey)} <small>契约 ${escapeHtml(shortHash)}</small></h3>
    ${capability.requiresMotionSafetyParameters ? `<div class="risk-box">InlinePose 会直接驱动物理机械臂。现场首次执行必须使用 COMMISSIONING_LOW，发布前核对坐标系、单位与差异。</div>` : ""}
    <div class="binding-list">${fields}</div>${advancedNodeJson(node)}</section>`;
}

function bindingField(node, name, schema) {
  const value = node.with?.[name];
  if (name === "pose") {
    const inline = value?.inlinePose;
    const mode = inline ? "inline" : "ref";
    return `<div class="binding-field"><span>pose <small>OBJECT · 互斥</small></span>
      <select data-pose-mode><option value="inline" ${mode === "inline" ? "selected" : ""}>InlinePose（调试）</option><option value="ref" ${mode === "ref" ? "selected" : ""}>poseRef（目录）</option></select>
      ${mode === "inline" ? `<div class="form-grid">${["x","y","z","rx","ry","rz"].map(key => `<label>${key.toUpperCase()}<input data-pose-coordinate="${key}" type="number" step="0.01" value="${inline?.[key] ?? 0}"></label>`).join("")}</div>` : `<input data-pose-ref value="${escapeHtml(value?.poseRef || "")}" placeholder="例如 CACHE_A.PICK.A">`}
    </div>`;
  }
  if (schema.type === "BOOLEAN") return `<label class="binding-field">${escapeHtml(name)} <small>BOOLEAN</small><select data-binding="${escapeHtml(name)}" data-type="BOOLEAN"><option value="true" ${value === true ? "selected" : ""}>true</option><option value="false" ${value === false ? "selected" : ""}>false</option></select></label>`;
  const type = schema.type === "NUMBER" || schema.type === "INTEGER" ? "number" : "text";
  const displayed = typeof value === "object" ? JSON.stringify(value) : value ?? "";
  return `<label class="binding-field">${escapeHtml(name)} <small>${escapeHtml(schema.type)}${schema.unit ? ` · ${escapeHtml(schema.unit)}` : ""}</small><input data-binding="${escapeHtml(name)}" data-type="${escapeHtml(schema.type)}" type="${type}" value="${escapeHtml(displayed)}"></label>`;
}

function controlSection(node) {
  return `<section class="inspector-section"><h3>${escapeHtml(node.kind)} 受限控制节点</h3>
    <div class="risk-box">控制节点不支持脚本、反射或任意并行。嵌套内容可在高级 JSON 中编辑，保存后仍必须通过 Compiler。</div>
    ${advancedNodeJson(node)}</section>`;
}

function actionReferenceSection(node) {
  const catalogItem = findCatalogItem(node.actionRef?.actionKey, node.actionRef?.version);
  const identity = `${node.actionRef?.actionKey || "未配置"} @ ${node.actionRef?.version || "未配置"}`;
  if (!catalogItem) {
    return `<section class="inspector-section"><h3>引用动作</h3>
      <div class="reference-identity">${escapeHtml(identity)}</div>
      <div class="risk-box">这个固定版本已不在发布动作目录中。Compiler 将拒绝发布，请删除该节点或恢复依赖版本。</div>
      ${advancedNodeJson(node)}</section>`;
  }

  const missing = getMissingRequiredBindings(node, catalogItem);
  const fields = Object.entries(catalogItem.inputSchema || {})
    .map(([name, schema]) => referenceBindingField(node, name, schema))
    .join("");
  return `<section class="inspector-section action-reference-section">
    <div class="reference-heading"><div><h3>${escapeHtml(catalogItem.displayName || catalogItem.actionKey)}</h3><div class="reference-identity">${escapeHtml(identity)}</div></div>
      <span class="binding-status ${missing.length ? "pending" : "ready"}">${missing.length ? `${missing.length} 项待配置` : "绑定完整"}</span></div>
    <p class="reference-description">${escapeHtml(catalogItem.description || "已发布组合动作")}</p>
    ${catalogItem.hasPhysicalSideEffect ? `<div class="risk-box">该组合动作包含物理副作用。引用时固定到当前发布版本，执行前仍受原动作安全约束。</div>` : ""}
    ${fields ? `<div class="reference-binding-list">${fields}</div>` : `<div class="binding-empty">这个动作没有输入参数，可直接引用。</div>`}
    ${advancedNodeJson(node)}</section>`;
}

function referenceBindingField(node, name, schema) {
  const hasBinding = Object.prototype.hasOwnProperty.call(node.with || {}, name);
  const value = node.with?.[name];
  const mode = !hasBinding ? "UNBOUND" : typeof value === "string" && value.startsWith("$") ? "EXPRESSION" : "LITERAL";
  const displayed = mode === "LITERAL" && typeof value === "object" ? JSON.stringify(value) : value ?? "";
  const parentSchema = state.definition.inputSchema?.[name];
  const parentCompatible = parentSchema && areSchemaTypesCompatible(parentSchema.type, schema.type);
  const requiredLabel = schema.required ? `<b class="required-mark">必填</b>` : `<span>可选</span>`;
  const quickAction = parentCompatible
    ? `<button type="button" class="binding-button" data-use-parent-input="${escapeHtml(name)}">绑定 $input.${escapeHtml(name)}</button>`
    : parentSchema
      ? `<span class="binding-conflict">父输入同名但类型不兼容</span>`
      : `<button type="button" class="binding-button promote" data-promote-input="${escapeHtml(name)}">提升为当前 Action 输入</button>`;
  return `<div class="reference-binding-row ${schema.required && !hasBinding ? "missing" : ""}">
    <div class="binding-row-heading"><label for="reference-value-${escapeHtml(name)}">${escapeHtml(name)}</label><small>${escapeHtml(schema.type)}${schema.unit ? ` · ${escapeHtml(schema.unit)}` : ""}</small>${requiredLabel}</div>
    <div class="reference-binding-controls">
      <select data-reference-mode="${escapeHtml(name)}" aria-label="${escapeHtml(name)} 绑定方式">
        <option value="UNBOUND" ${mode === "UNBOUND" ? "selected" : ""}>未绑定</option>
        <option value="EXPRESSION" ${mode === "EXPRESSION" ? "selected" : ""}>上游表达式</option>
        <option value="LITERAL" ${mode === "LITERAL" ? "selected" : ""}>固定值</option>
      </select>
      <input id="reference-value-${escapeHtml(name)}" data-reference-value="${escapeHtml(name)}" value="${escapeHtml(displayed)}" ${mode === "UNBOUND" ? "disabled" : ""} placeholder="${mode === "EXPRESSION" ? "$input.parameter" : literalPlaceholder(schema.type)}">
    </div>
    <div class="binding-actions">${quickAction}</div>
  </div>`;
}

function findCatalogItem(actionKey, version) {
  return state.actionCatalog.find(item => item.actionKey === actionKey && item.version === version);
}

function areSchemaTypesCompatible(sourceType, targetType) {
  return sourceType === targetType || (sourceType === "INTEGER" && targetType === "NUMBER");
}

function literalPlaceholder(type) {
  if (type === "OBJECT") return '{"key":"value"}';
  if (type === "ARRAY") return '["value"]';
  if (type === "BOOLEAN") return "true / false";
  return "请输入固定值";
}

function advancedNodeJson(node) {
  return `<label class="field-label">高级节点 JSON</label><textarea id="advancedNodeJson" class="textarea advanced-json">${escapeHtml(JSON.stringify(node, null, 2))}</textarea><button id="applyNodeJson" class="mini-button">应用节点 JSON</button>`;
}

function actionSchemaSection() {
  return `<section class="inspector-section"><h3>Action 输入 Schema</h3><textarea id="actionInputSchema" class="textarea advanced-json">${escapeHtml(JSON.stringify(state.definition.inputSchema || {}, null, 2))}</textarea><button id="applyInputSchema" class="mini-button">应用输入 Schema</button></section>`;
}

function bindInspector(node) {
  document.querySelectorAll("[data-node-field]").forEach(input => input.addEventListener("change", () => {
    let value = input.value;
    if (input.dataset.nodeField === "timeoutMs") value = Number(value);
    if (input.dataset.nodeField === "gate") value = value === "true";
    node[input.dataset.nodeField] = value; markDirty(); renderCanvas();
  }));
  document.querySelectorAll("[data-binding]").forEach(input => input.addEventListener("change", () => {
    node.with ||= {};
    node.with[input.dataset.binding] = parseBinding(input.value, input.dataset.type);
    markDirty();
  }));
  document.querySelectorAll("[data-reference-mode]").forEach(select => select.addEventListener("change", () => {
    const parameterName = select.dataset.referenceMode;
    if (select.value === "UNBOUND") {
      state.definition = updateReferenceBinding(state.definition, state.selectedPath, parameterName, undefined);
      markDirty(); renderCanvas(); renderInspector();
      return;
    }
    const valueInput = document.querySelector(`[data-reference-value="${CSS.escape(parameterName)}"]`);
    valueInput.disabled = false;
    if (select.value === "EXPRESSION" && !valueInput.value) valueInput.value = `$input.${parameterName}`;
    valueInput.placeholder = select.value === "EXPRESSION" ? "$input.parameter" : literalPlaceholder(referenceSchema(node, parameterName)?.type);
    valueInput.focus();
  }));
  document.querySelectorAll("[data-reference-value]").forEach(input => input.addEventListener("change", () => {
    const parameterName = input.dataset.referenceValue;
    const schema = referenceSchema(node, parameterName);
    const mode = document.querySelector(`[data-reference-mode="${CSS.escape(parameterName)}"]`)?.value;
    if (!schema || mode === "UNBOUND") return;
    try {
      const value = parseReferenceBindingValue(input.value, schema, mode);
      state.definition = updateReferenceBinding(state.definition, state.selectedPath, parameterName, value);
      markDirty(); renderCanvas(); renderInspector();
    } catch (error) {
      notify(`${parameterName} 配置无效：${error.message}`, true);
      input.focus();
    }
  }));
  document.querySelectorAll("[data-use-parent-input]").forEach(button => button.addEventListener("click", () => {
    const parameterName = button.dataset.useParentInput;
    state.definition = updateReferenceBinding(state.definition, state.selectedPath, parameterName, `$input.${parameterName}`);
    markDirty(); renderCanvas(); renderInspector();
  }));
  document.querySelectorAll("[data-promote-input]").forEach(button => button.addEventListener("click", () => {
    const parameterName = button.dataset.promoteInput;
    const catalogItem = findCatalogItem(node.actionRef?.actionKey, node.actionRef?.version);
    if (!catalogItem) return notify("引用动作版本已不在目录中。", true);
    try {
      state.definition = promoteReferenceInput(state.definition, state.selectedPath, catalogItem, parameterName);
      markDirty(); renderCanvas(); renderInspector();
      notify(`${parameterName} 已提升为当前 Action 输入，并完成引用绑定。`);
    } catch (error) { notify(`无法提升输入：${error.message}`, true); }
  }));
  $("[data-pose-mode]")?.addEventListener("change", event => {
    node.with.pose = event.target.value === "inline"
      ? { inlinePose: { frame: "BASE", unit: "MILLIMETER_DEGREE", x: 0, y: 0, z: 300, rx: 180, ry: 0, rz: 0 } }
      : { poseRef: "" };
    markDirty(); renderInspector();
  });
  document.querySelectorAll("[data-pose-coordinate]").forEach(input => input.addEventListener("change", () => {
    node.with.pose.inlinePose[input.dataset.poseCoordinate] = Number(input.value); markDirty();
  }));
  $("[data-pose-ref]")?.addEventListener("change", event => { node.with.pose.poseRef = event.target.value; markDirty(); });
  $("#applyNodeJson")?.addEventListener("click", () => {
    try {
      const replacement = JSON.parse($("#advancedNodeJson").value);
      state.definition = replaceNodeAtPath(state.definition, state.selectedPath, replacement);
      markDirty(); renderCanvas(); renderInspector();
    }
    catch (error) { notify(`节点 JSON 无效：${error.message}`, true); }
  });
  $("#applyInputSchema")?.addEventListener("click", () => {
    try { state.definition.inputSchema = JSON.parse($("#actionInputSchema").value); markDirty(); renderCanvas(); renderInspector(); notify("输入 Schema 已应用，发布前请编译。"); }
    catch (error) { notify(`输入 Schema JSON 无效：${error.message}`, true); }
  });
}

function referenceSchema(node, parameterName) {
  return findCatalogItem(node?.actionRef?.actionKey, node?.actionRef?.version)?.inputSchema?.[parameterName];
}

function parseBinding(value, type) {
  if (value.startsWith("$")) return value;
  if (type === "BOOLEAN") return value === "true";
  if (type === "NUMBER") return Number(value);
  if (type === "INTEGER") return Math.trunc(Number(value));
  if (type === "OBJECT" || type === "ARRAY") { try { return JSON.parse(value); } catch { return value; } }
  return value;
}

function markDirty() { state.dirty = true; state.compile = null; $("#dirtyState").textContent = "有未保存修改"; renderCompile(); }
function currentRelease() {
  return state.releases.find(item =>
    item.actionKey === state.definition?.actionKey && item.actionVersion === state.definition?.version) || null;
}
function currentLifecycle() { return describeActionLifecycle({ draft: state.draft, release: currentRelease() }); }
function isLocked() { return !currentLifecycle().canEdit; }
function updateLockState() {
  const lifecycle = currentLifecycle();
  const locked = !lifecycle.canEdit;
  $("#saveButton").disabled = locked;
  $("#compileButton").disabled = locked;
  $("#deleteButton").disabled = locked;
  $("#cloneButton").disabled = !currentRelease();
  const lifecycleButton = $("#lifecycleActionButton");
  lifecycleButton.textContent = lifecycle.canDeleteDraft
    ? "删除草稿"
    : lifecycle.state === "DEPRECATED"
      ? "版本已下线"
      : lifecycle.canDeprecateRelease ? "下线版本" : "尚未保存";
  lifecycleButton.disabled = !lifecycle.canDeleteDraft && !lifecycle.canDeprecateRelease;
  ["displayName", "description", "actionVersion", "scope"].forEach(id => { $(`#${id}`).disabled = locked; });
  $("#actionKey").disabled = locked || Boolean(state.draft);
  $("#entryPoint").disabled = locked;
  $("#nodeInspector").inert = locked;
  updateNodeToolbarState();
  setStatus(`${isReleasePreview(state.draft) ? "只读查看" : lifecycle.statusLabel} · MySQL`);
  renderActionCatalog();
}

function updateNodeToolbarState() {
  let location = null;
  try { location = state.selectedPath ? getNodeLocation(state.definition, state.selectedPath) : null; }
  catch { state.selectedPath = null; }
  $("#moveUpButton").disabled = isLocked() || !location || location.index <= 0;
  $("#moveDownButton").disabled = isLocked() || !location || location.index >= location.siblings.length - 1;
  $("#deleteButton").disabled = isLocked() || !location;
}

function deleteSelected() {
  if (!state.selectedPath || isLocked()) return;
  try {
    const location = getNodeLocation(state.definition, state.selectedPath);
    const result = removeNodeAtPath(state.definition, state.selectedPath);
    state.definition = result.definition;
    const remainingCount = location.siblings.length - 1;
    if (remainingCount > 0) {
      const nextPath = cloneNodePath(state.selectedPath);
      nextPath[nextPath.length - 1].index = Math.min(location.index, remainingCount - 1);
      state.selectedPath = nextPath;
    } else {
      state.selectedPath = location.parentPath.length ? cloneNodePath(location.parentPath) : null;
    }
    markDirty(); renderCanvas(); renderInspector();
    notify(`${result.removedNode.displayName || result.removedNode.stepId} 已删除`);
  } catch (error) { notify(`删除节点失败：${error.message}`, true); }
}

function moveSelected(delta) {
  if (!state.selectedPath || isLocked()) return;
  try {
    const location = getNodeLocation(state.definition, state.selectedPath);
    const target = location.index + delta;
    if (target < 0 || target >= location.siblings.length) return;
    reorderNode(state.selectedPath, delta < 0 ? location.index - 1 : location.index + 2);
  } catch (error) { notify(`调整顺序失败：${error.message}`, true); }
}

function reorderNode(nodePath, insertionIndex) {
  if (isLocked()) return;
  try {
    const movedNode = getNodeAtPath(state.definition, nodePath);
    const result = moveNodeAtInsertion(state.definition, nodePath, insertionIndex);
    state.selectedPath = result.nodePath;
    if (!result.changed) return renderCanvas();
    state.definition = result.definition;
    markDirty();
    renderCanvas();
    renderInspector();
    notify(`${movedNode.displayName || movedNode.stepId} 已移动到同级第 ${result.toIndex + 1} 位`);
  } catch (error) { notify(`调整顺序失败：${error.message}`, true); }
}

async function saveDraft() {
  if (isLocked()) return notify("已发布版本不可修改。", true);
  try {
    const payload = { definition: state.definition };
    if (state.draft) { payload.draftId = state.draft.id; payload.expectedRevision = state.draft.revision; }
    state.draft = await api("/api/actions/drafts", { method: "POST", body: JSON.stringify(payload) });
    state.definition = clone(state.draft.definition);
    state.dirty = false;
    if (state.definition.entryPoint === true) {
      state.mainDraftId = state.draft.id;
      state.previewOrigin = null;
    } else if (state.mainDraftId === state.draft.id) {
      state.mainDraftId = null;
    }
    await refreshDrafts();
    renderAll();
    notify(`草稿已保存为 r${state.draft.revision}`);
    return state.draft;
  } catch (error) { notify(error.message, true); throw error; }
}

async function compileDraft() {
  try {
    if (state.dirty || !state.draft) await saveDraft();
    state.compile = await api(`/api/actions/drafts/${state.draft.id}/compile`, { method: "POST" });
    renderCompile();
    notify(state.compile.success ? "编译通过，可以发布。" : "编译未通过，请修正错误。", !state.compile.success);
    return state.compile;
  } catch (error) { notify(error.message, true); }
}

function renderCompile() {
  const result = state.compile;
  const badge = $("#compileBadge");
  if (!result) {
    badge.className = "badge neutral"; badge.textContent = "未编译";
    $("#compileSummary").textContent = "保存草稿后执行编译，查看能力依赖、节点上限和计划 Hash。";
    $("#issueList").innerHTML = ""; $("#publishButton").disabled = true; return;
  }
  badge.className = `badge ${result.success ? "success" : "error"}`;
  badge.textContent = result.success ? "通过" : "阻断";
  $("#compileSummary").innerHTML = result.success
    ? `<b>${result.plan.compiledNodeCount}</b> nodes / 最大展开 <b>${result.plan.maxExpandedNodeCount}</b><br>能力：${escapeHtml(result.requiredCapabilities.map(item => item.capabilityKey).join(", "))}<br>依赖：${escapeHtml(result.dependencies.map(item => `${item.actionKey}@${item.version}`).join(", ") || "无")}<br>Hash：${escapeHtml(result.planHash)}`
    : `${result.issues.filter(item => item.severity === "ERROR").length} 个错误阻止发布`;
  $("#issueList").innerHTML = result.issues.map(issue => `<div class="issue ${issue.severity === "WARNING" ? "warning" : ""}"><b>${escapeHtml(issue.code)}</b> · ${escapeHtml(issue.path)}<br>${escapeHtml(issue.message)}</div>`).join("");
  $("#publishButton").disabled = !result.success || isLocked();
}

async function publishDraft() {
  const summary = $("#changeSummary").value.trim();
  if (!summary) return notify("发布前必须填写变更说明。", true);
  if (!state.compile?.success) return notify("请先通过编译检查。", true);
  try {
    const release = await api(`/api/actions/drafts/${state.draft.id}/publish`, { method: "POST", body: JSON.stringify({ changeSummary: summary }) });
    state.draft = await api(`/api/actions/drafts/${state.draft.id}`);
    state.definition = clone(state.draft.definition);
    state.compile = null;
    await Promise.all([refreshDrafts(), refreshReleases(), refreshActionCatalog()]);
    renderAll();
    notify(`已发布 ${release.actionKey}@${release.actionVersion}，Hash ${release.planHash.slice(0, 12)}…`);
  } catch (error) { notify(error.message, true); }
}

function cloneRelease() {
  const release = currentRelease();
  if (!release) return notify("请先查看一个已发布版本，再基于它创建新版本。", true);
  openVersionDialog({ actionKey: release.actionKey, version: release.actionVersion, displayName: release.definition.displayName });
}

function openVersionDialog(source) {
  const usedVersions = [
    ...state.drafts.filter(item => item.actionKey === source.actionKey).map(item => item.definition.version),
    ...state.releases.filter(item => item.actionKey === source.actionKey).map(item => item.actionVersion)
  ];
  try {
    $("#versionActionKey").value = source.actionKey;
    $("#versionSourceVersion").value = source.version;
    $("#versionSource").textContent = `${source.displayName || source.actionKey} · ${source.actionKey}@${source.version}`;
    $("#newActionVersion").value = nextAvailablePatchVersion(source.version, usedVersions);
    $("#versionDialog").showModal();
    $("#newActionVersion").focus();
    $("#newActionVersion").select();
  } catch (error) { notify(`无法生成新版本号：${error.message}`, true); }
}

async function createVersionDraft(event) {
  event.preventDefault();
  if (!event.currentTarget.reportValidity()) return;
  if (!confirmDiscardChanges()) return;
  const actionKey = $("#versionActionKey").value;
  const sourceVersion = $("#versionSourceVersion").value;
  const newVersion = $("#newActionVersion").value.trim();
  try {
    const draft = await api("/api/actions/drafts/clone", { method: "POST", body: JSON.stringify({ actionKey, sourceVersion, newVersion }) });
    $("#versionDialog").close();
    await refreshDrafts();
    await openDraft(draft.id);
    notify(`已创建 ${actionKey}@${newVersion} 草稿，可以开始编辑。`);
  } catch (error) { notify(error.message, true); }
}

function openLifecycleDialog() {
  const lifecycle = currentLifecycle();
  if (lifecycle.canDeleteDraft) {
    pendingLifecycleAction = { type: "DELETE_DRAFT", draftId: state.draft.id, revision: state.draft.revision };
    $("#lifecycleDialogTitle").textContent = "删除 Action 草稿";
    $("#lifecycleDialogDescription").textContent = `${state.definition.displayName || state.definition.actionKey} · ${state.definition.actionKey}@${state.definition.version}`;
    $("#lifecycleDialogWarning").textContent = "草稿及其未发布修改会被永久删除。该操作不会删除任何已发布版本。";
    $("#confirmLifecycleButton").textContent = "确认删除草稿";
  } else if (lifecycle.canDeprecateRelease) {
    pendingLifecycleAction = { type: "DEPRECATE_RELEASE", actionKey: state.definition.actionKey, version: state.definition.version };
    $("#lifecycleDialogTitle").textContent = "下线发布版本";
    $("#lifecycleDialogDescription").textContent = `${state.definition.displayName || state.definition.actionKey} · ${state.definition.actionKey}@${state.definition.version}`;
    $("#lifecycleDialogWarning").textContent = "下线后不可再被调度新选用；若为组合动作，也不会再出现在引用库中。发布工件和历史引用仍会保留用于审计与回放。";
    $("#confirmLifecycleButton").textContent = "确认下线版本";
  } else return;
  $("#lifecycleDialog").showModal();
}

async function executeLifecycleAction(event) {
  event.preventDefault();
  if (!pendingLifecycleAction) return;
  const action = pendingLifecycleAction;
  $("#confirmLifecycleButton").disabled = true;
  try {
    if (action.type === "DELETE_DRAFT") {
      const returnDraftId = state.previewOrigin?.draftId || state.mainDraftId;
      await api(`/api/actions/drafts/${action.draftId}?expectedRevision=${action.revision}`, { method: "DELETE" });
      $("#lifecycleDialog").close();
      pendingLifecycleAction = null;
      state.draft = null;
      state.definition = null;
      state.dirty = false;
      await refreshDrafts();
      const mainActions = listMainActionDrafts(state.drafts);
      const next = mainActions.find(item => item.id === returnDraftId) ||
        mainActions.find(item => item.status === "DRAFT") || mainActions[0];
      if (next) await openDraft(next.id); else createNewMainDefinition();
      notify("Action 草稿已删除。");
    } else {
      await api(`/api/actions/releases/${encodeURIComponent(action.actionKey)}/${encodeURIComponent(action.version)}/deprecate`, { method: "POST" });
      $("#lifecycleDialog").close();
      pendingLifecycleAction = null;
      await Promise.all([refreshReleases(), refreshActionCatalog()]);
      renderDraftOptions();
      renderAll();
      notify(`${action.actionKey}@${action.version} 已下线，历史发布工件仍然保留。`);
    }
  } catch (error) { notify(error.message, true); }
  finally { $("#confirmLifecycleButton").disabled = false; }
}

async function refreshDrafts() {
  state.drafts = await api("/api/actions/drafts");
  renderDraftOptions();
  renderCompositeDrafts();
}
async function refreshReleases() { state.releases = await api("/api/actions/releases"); renderVersionOptions(); }
async function refreshActionCatalog() { state.actionCatalog = await api("/api/action-catalog"); renderActionCatalog(); }
function renderVersionOptions() {
  const releases = state.releases.filter(item => !state.definition?.actionKey || item.actionKey === state.definition.actionKey);
  const options = releases.map(item => `<option value="${escapeHtml(item.actionVersion)}">${escapeHtml(item.actionVersion)} · ${item.status}</option>`).join("");
  $("#fromVersion").innerHTML = options;
  $("#toVersion").innerHTML = options;
  if (releases.length > 1) $("#fromVersion").selectedIndex = 1;
}

async function compareVersions() {
  const from = $("#fromVersion").value, to = $("#toVersion").value, key = state.definition?.actionKey;
  if (!key || !from || !to || from === to) return notify("请选择同一 Action 的两个不同版本。", true);
  try {
    const result = await api(`/api/actions/releases/${encodeURIComponent(key)}/${encodeURIComponent(from)}/diff/${encodeURIComponent(to)}`);
    $("#diffResult").innerHTML = result.changes.length ? result.changes.map(change => `<div class="diff-row ${change.risk.toLowerCase()}"><b>${escapeHtml(change.kind)} · ${escapeHtml(change.risk)}</b><br>${escapeHtml(change.path)}<br>${escapeHtml(change.before ?? "∅")} → ${escapeHtml(change.after ?? "∅")}</div>`).join("") : `<div class="diff-row">执行语义没有差异</div>`;
  } catch (error) { notify(error.message, true); }
}

initialize();

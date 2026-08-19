(function exposeActionCanvas(root, factory) {
  const actionReference = typeof module === "object" && module.exports
    ? require("./action-reference.js")
    : root.ActionReference;
  const api = factory(actionReference);
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionCanvas = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionCanvasApi(ActionReference) {
  "use strict";

  const ACTION_CATALOG_MIME = "application/x-kunling-action-catalog-item";

  const GEOMETRY = Object.freeze({
    top: 38,
    left: 42,
    bottom: 70,
    nodeHeight: 68,
    nodeGap: 17,
    compositePreviewTop: 13,
    compositePreviewBottom: 12,
    atomicRowHeight: 38,
    atomicRowGap: 7,
    branchLabelHeight: 23,
    depthIndent: 42,
    minimumNodeWidth: 300,
    maximumNodeWidth: 820
  });

  const COLORS = Object.freeze({
    ink: "#14262c",
    muted: "#6c7c80",
    paper: "#f7f9f5",
    card: "rgba(255,255,255,.96)",
    line: "#cad3cd",
    lineStrong: "#9baaa4",
    petrol: "#0e5b62",
    petrolSoft: "#dce9e5",
    signal: "#f1a431",
    signalSoft: "#fff1d7",
    danger: "#c84b3c"
  });

  function pathKey(path) {
    return (path || []).map(segment => `${segment.key}:${segment.index}`).join("/");
  }

  function pathsEqual(left, right) {
    return Array.isArray(left) && Array.isArray(right) && left.length === right.length &&
      left.every((segment, index) => segment.key === right[index]?.key && segment.index === right[index]?.index);
  }

  function containerId(parentPath, containerKey) {
    const owner = pathKey(parentPath);
    return `${owner || "root"}/${containerKey}`;
  }

  function buildWorkflowLayout(definition, capabilities, viewportWidth, actionCatalog = []) {
    const nodes = [];
    const physicalCapabilities = new Set((capabilities || [])
      .filter(capability => capability.sideEffect === "PHYSICAL")
      .map(capability => capability.capabilityKey));
    const catalogByIdentity = new Map((actionCatalog || []).map(item => [`${item.actionKey}@${item.version}`, item]));
    const safeViewportWidth = Number.isFinite(viewportWidth) ? Math.max(viewportWidth, 360) : 760;
    let cursorY = GEOMETRY.top;

    function visitContainer(items, key, parentPath, depth, branchLabel) {
      if (!Array.isArray(items) || items.length === 0) return;
      const currentContainerId = containerId(parentPath, key);

      items.forEach((node, index) => {
        const label = index === 0 ? branchLabel : null;
        if (label) cursorY += GEOMETRY.branchLabelHeight;
        const path = [...parentPath, { key, index }];
        const referencedAction = node.kind === "ACTION_REF"
          ? catalogByIdentity.get(`${node.actionRef?.actionKey}@${node.actionRef?.version}`)
          : null;
        const x = GEOMETRY.left + depth * GEOMETRY.depthIndent;
        const width = Math.min(
          GEOMETRY.maximumNodeWidth,
          Math.max(GEOMETRY.minimumNodeWidth, safeViewportWidth - x - GEOMETRY.left)
        );
        const atomicSteps = Array.isArray(referencedAction?.atomicSteps)
          ? referencedAction.atomicSteps
          : [];
        const compositeGroup = node.kind === "ACTION_REF";
        const previewHeight = atomicSteps.length === 0
          ? 0
          : GEOMETRY.compositePreviewTop + GEOMETRY.compositePreviewBottom +
            atomicSteps.length * GEOMETRY.atomicRowHeight +
            Math.max(0, atomicSteps.length - 1) * GEOMETRY.atomicRowGap;
        const entry = {
          node,
          path,
          pathKey: pathKey(path),
          parentPathKey: pathKey(parentPath),
          containerId: currentContainerId,
          containerKey: key,
          containerSize: items.length,
          index,
          depth,
          branchLabel: label,
          x,
          y: cursorY,
          width,
          height: GEOMETRY.nodeHeight + previewHeight,
          compositeGroup,
          atomicSteps,
          physical: node.kind === "CAPABILITY"
            ? physicalCapabilities.has(node.capabilityKey)
            : Boolean(referencedAction?.hasPhysicalSideEffect),
          pendingBindingCount: referencedAction
            ? ActionReference.getMissingRequiredBindings(node, referencedAction).length
            : 0
        };
        nodes.push(entry);
        cursorY += entry.height + GEOMETRY.nodeGap;

        if (node.kind === "FOREACH") {
          visitContainer(node.steps, "steps", path, depth + 1, "LOOP BODY");
        } else if (node.kind === "CONDITION") {
          visitContainer(node.then, "then", path, depth + 1, "THEN");
          visitContainer(node.else, "else", path, depth + 1, "ELSE");
        }
      });
    }

    visitContainer(definition?.steps, "steps", [], 0, null);
    return {
      nodes,
      width: safeViewportWidth,
      height: Math.max(340, cursorY + GEOMETRY.bottom)
    };
  }

  function roundedRect(context, x, y, width, height, radius) {
    const safeRadius = Math.min(radius, width / 2, height / 2);
    context.beginPath();
    context.moveTo(x + safeRadius, y);
    context.arcTo(x + width, y, x + width, y + height, safeRadius);
    context.arcTo(x + width, y + height, x, y + height, safeRadius);
    context.arcTo(x, y + height, x, y, safeRadius);
    context.arcTo(x, y, x + width, y, safeRadius);
    context.closePath();
  }

  function truncateText(context, value, maximumWidth) {
    const text = String(value ?? "");
    if (context.measureText(text).width <= maximumWidth) return text;
    let low = 0;
    let high = text.length;
    while (low < high) {
      const middle = Math.ceil((low + high) / 2);
      if (context.measureText(`${text.slice(0, middle)}…`).width <= maximumWidth) low = middle;
      else high = middle - 1;
    }
    return `${text.slice(0, low)}…`;
  }

  class WorkflowCanvas {
    constructor({ canvas, viewport, liveRegion, onSelect, onMove, onExternalDrop, onNotice }) {
      if (!canvas || !viewport) throw new TypeError("canvas and viewport are required.");
      this.canvas = canvas;
      this.viewport = viewport;
      this.liveRegion = liveRegion || null;
      this.onSelect = onSelect || (() => {});
      this.onMove = onMove || (() => {});
      this.onExternalDrop = onExternalDrop || (() => {});
      this.onNotice = onNotice || (() => {});
      this.context = canvas.getContext("2d");
      this.model = { definition: { steps: [] }, capabilities: [], selectedPath: null, locked: false };
      this.layout = { nodes: [], width: 0, height: 340 };
      this.drag = null;
      this.externalDrop = null;
      this.bindEvents();
      this.resizeObserver = typeof ResizeObserver === "function"
        ? new ResizeObserver(() => this.redraw())
        : null;
      this.resizeObserver?.observe(viewport);
    }

    bindEvents() {
      this.canvas.addEventListener("pointerdown", event => this.handlePointerDown(event));
      this.canvas.addEventListener("pointermove", event => this.handlePointerMove(event));
      this.canvas.addEventListener("pointerup", event => this.handlePointerUp(event));
      this.canvas.addEventListener("pointercancel", () => this.cancelDrag());
      this.canvas.addEventListener("pointerleave", () => {
        if (!this.drag) this.canvas.style.cursor = "default";
      });
      this.canvas.addEventListener("keydown", event => this.handleKeyboard(event));
      this.canvas.addEventListener("dragover", event => this.handleExternalDragOver(event));
      this.canvas.addEventListener("dragleave", () => this.clearExternalDrop());
      this.canvas.addEventListener("drop", event => this.handleExternalDrop(event));
    }

    render(model) {
      this.model = {
        definition: model?.definition || { steps: [] },
        capabilities: model?.capabilities || [],
        actionCatalog: model?.actionCatalog || [],
        selectedPath: model?.selectedPath || null,
        locked: Boolean(model?.locked)
      };
      this.redraw();
    }

    redraw() {
      if (!this.context) return;
      const viewportWidth = Math.max(this.viewport.clientWidth || 0, 360);
      this.layout = buildWorkflowLayout(
        this.model.definition,
        this.model.capabilities,
        viewportWidth,
        this.model.actionCatalog);
      const pixelRatio = Math.max(1, window.devicePixelRatio || 1);
      this.canvas.style.width = `${this.layout.width}px`;
      this.canvas.style.height = `${this.layout.height}px`;
      this.canvas.width = Math.round(this.layout.width * pixelRatio);
      this.canvas.height = Math.round(this.layout.height * pixelRatio);
      this.context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      this.draw();
    }

    draw() {
      const context = this.context;
      context.clearRect(0, 0, this.layout.width, this.layout.height);
      this.drawGrid(context);
      this.drawConnectors(context);
      this.layout.nodes.forEach(entry => this.drawNode(context, entry));
      if (this.drag?.active && this.drag.drop) this.drawDropIndicator(context, this.drag.drop);
      if (this.externalDrop) this.drawDropIndicator(context, this.externalDrop);
      if (this.drag?.active) this.drawDragGhost(context);
    }

    drawGrid(context) {
      context.save();
      context.fillStyle = COLORS.paper;
      context.fillRect(0, 0, this.layout.width, this.layout.height);
      context.fillStyle = "rgba(14,91,98,.10)";
      for (let x = 18; x < this.layout.width; x += 22) {
        for (let y = 18; y < this.layout.height; y += 22) context.fillRect(x, y, 1, 1);
      }
      context.restore();
    }

    drawConnectors(context) {
      const groups = new Map();
      this.layout.nodes.forEach(entry => {
        if (!groups.has(entry.containerId)) groups.set(entry.containerId, []);
        groups.get(entry.containerId).push(entry);
      });
      context.save();
      context.strokeStyle = COLORS.lineStrong;
      context.lineWidth = 1;
      context.setLineDash([4, 5]);
      groups.forEach(entries => {
        if (entries.length < 2) return;
        const railX = entries[0].x - 18;
        context.beginPath();
        context.moveTo(railX, entries[0].y + entries[0].height / 2);
        context.lineTo(railX, entries[entries.length - 1].y + entries[entries.length - 1].height / 2);
        context.stroke();
        entries.forEach(entry => {
          context.beginPath();
          context.moveTo(railX, entry.y + entry.height / 2);
          context.lineTo(entry.x, entry.y + entry.height / 2);
          context.stroke();
        });
      });
      groups.forEach(entries => {
        const first = entries[0];
        if (!first || first.depth === 0) return;
        const parent = this.layout.nodes.find(entry => entry.pathKey === first.parentPathKey);
        if (!parent) return;
        const railX = first.x - 18;
        context.beginPath();
        context.moveTo(parent.x + 18, parent.y + parent.height);
        context.lineTo(parent.x + 18, first.y - 12);
        context.lineTo(railX, first.y - 12);
        context.lineTo(railX, first.y + first.height / 2);
        context.stroke();
      });
      context.restore();
    }

    drawNode(context, entry, override = {}) {
      const selected = !override.ghost && pathsEqual(this.model.selectedPath, entry.path);
      const dragging = this.drag?.active && pathsEqual(this.drag.source.path, entry.path);
      const x = override.x ?? entry.x;
      const y = override.y ?? entry.y;
      const opacity = override.opacity ?? (dragging ? 0.24 : 1);
      const headerHeight = entry.compositeGroup ? GEOMETRY.nodeHeight : entry.height;
      const stripeColor = entry.physical || entry.pendingBindingCount ? COLORS.signal : COLORS.petrol;
      context.save();
      context.globalAlpha = opacity;

      if (entry.branchLabel && !override.ghost) {
        context.fillStyle = COLORS.petrol;
        context.font = "700 9px Bahnschrift, sans-serif";
        context.fillText(entry.branchLabel, x + 1, y - 9);
      }

      context.shadowColor = selected ? "rgba(14,91,98,.18)" : "rgba(29,56,56,.08)";
      context.shadowBlur = selected ? 17 : 10;
      context.shadowOffsetY = selected ? 6 : 4;
      roundedRect(context, x, y, entry.width, entry.height, 5);
      context.fillStyle = COLORS.card;
      context.fill();
      context.shadowColor = "transparent";
      context.lineWidth = selected ? 2 : 1;
      context.strokeStyle = selected ? COLORS.petrol : COLORS.line;
      context.setLineDash(entry.compositeGroup ? [7, 5] : []);
      context.stroke();
      context.setLineDash([]);

      roundedRect(context, x, y, 5, headerHeight, 4);
      context.fillStyle = stripeColor;
      context.fill();

      context.beginPath();
      context.arc(x + 31, y + headerHeight / 2, 15, 0, Math.PI * 2);
      context.fillStyle = stripeColor;
      context.fill();
      context.fillStyle = entry.physical ? "#3a290e" : "#fff";
      context.font = "700 10px Bahnschrift, sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText(String(entry.index + 1).padStart(2, "0"), x + 31, y + headerHeight / 2 + .5);

      const kind = entry.compositeGroup ? "组合引用" : String(entry.node.kind || "NODE");
      context.font = "700 8px Bahnschrift, sans-serif";
      const pillWidth = Math.max(52, context.measureText(kind).width + 16);
      roundedRect(context, x + entry.width - pillWidth - 35, y + 13, pillWidth, 20, 2);
      context.fillStyle = entry.physical || entry.pendingBindingCount ? COLORS.signalSoft : COLORS.petrolSoft;
      context.fill();
      context.fillStyle = entry.physical || entry.pendingBindingCount ? "#704b11" : COLORS.petrol;
      context.textAlign = "center";
      context.fillText(kind, x + entry.width - pillWidth / 2 - 35, y + 23.5);

      const textX = x + 58;
      const textWidth = Math.max(90, entry.width - pillWidth - 132);
      context.textAlign = "left";
      context.textBaseline = "alphabetic";
      context.fillStyle = COLORS.ink;
      context.font = "600 12px 'Microsoft YaHei', sans-serif";
      context.fillText(truncateText(context, entry.node.displayName || entry.node.stepId || "未命名节点", textWidth), textX, y + 27);
      context.fillStyle = COLORS.muted;
      context.font = "10px Bahnschrift, 'Microsoft YaHei', sans-serif";
      const detail = `${entry.node.stepId || "-"} · ${this.nodeDetail(entry.node, entry.pendingBindingCount)}`;
      context.fillText(truncateText(context, detail, textWidth + pillWidth - 4), textX, y + 47);

      context.fillStyle = this.model.locked ? COLORS.lineStrong : COLORS.petrol;
      for (let column = 0; column < 2; column += 1) {
        for (let row = 0; row < 3; row += 1) {
          context.beginPath();
          context.arc(x + entry.width - 17 + column * 5, y + 27 + row * 6, 1.25, 0, Math.PI * 2);
          context.fill();
        }
      }
      if (entry.compositeGroup && entry.atomicSteps.length > 0)
        this.drawAtomicPreview(context, entry, x, y + GEOMETRY.nodeHeight);
      context.restore();
    }

    drawAtomicPreview(context, entry, x, previewTop) {
      context.save();
      context.beginPath();
      context.moveTo(x + 18, previewTop);
      context.lineTo(x + entry.width - 18, previewTop);
      context.strokeStyle = COLORS.line;
      context.lineWidth = 1;
      context.stroke();

      entry.atomicSteps.forEach((step, index) => {
        const depth = Math.max(0, Number(step.depth) || 0);
        const indent = Math.min(depth, 3) * 12;
        const rowX = x + 22 + indent;
        const rowY = previewTop + GEOMETRY.compositePreviewTop +
          index * (GEOMETRY.atomicRowHeight + GEOMETRY.atomicRowGap);
        const rowWidth = entry.width - 44 - indent;
        roundedRect(context, rowX, rowY, rowWidth, GEOMETRY.atomicRowHeight, 3);
        context.fillStyle = depth > 0 ? "rgba(220,233,229,.42)" : "rgba(247,249,245,.94)";
        context.fill();
        context.strokeStyle = COLORS.line;
        context.lineWidth = 1;
        context.stroke();

        context.beginPath();
        context.arc(rowX + 18, rowY + GEOMETRY.atomicRowHeight / 2, 10, 0, Math.PI * 2);
        context.fillStyle = entry.physical ? COLORS.signalSoft : COLORS.petrolSoft;
        context.fill();
        context.fillStyle = entry.physical ? "#704b11" : COLORS.petrol;
        context.font = "700 8px Bahnschrift, sans-serif";
        context.textAlign = "center";
        context.textBaseline = "middle";
        context.fillText(`A${String(index + 1).padStart(2, "0")}`, rowX + 18, rowY + GEOMETRY.atomicRowHeight / 2 + .5);

        const textX = rowX + 36;
        const textWidth = Math.max(80, rowWidth - 48);
        context.textAlign = "left";
        context.textBaseline = "alphabetic";
        context.fillStyle = COLORS.ink;
        context.font = "600 10px 'Microsoft YaHei', sans-serif";
        context.fillText(truncateText(context, step.displayName || step.stepId || "未命名原子动作", textWidth), textX, rowY + 15);
        context.fillStyle = COLORS.muted;
        context.font = "9px Bahnschrift, 'Microsoft YaHei', sans-serif";
        context.fillText(truncateText(context, step.capabilityKey || "CAPABILITY", textWidth), textX, rowY + 29);
      });
      context.restore();
    }

    nodeDetail(node, pendingBindingCount = 0) {
      if (node.kind === "CAPABILITY") return node.capabilityKey || "CAPABILITY";
      if (node.kind === "ACTION_REF") {
        const identity = `${node.actionRef?.actionKey || "未指定"}@${node.actionRef?.version || "-"}`;
        return pendingBindingCount ? `${identity} · 待配置 ${pendingBindingCount}` : `${identity} · 已绑定`;
      }
      if (node.kind === "FOREACH") return `FOREACH ${node.items || "items"}`;
      if (node.kind === "CONDITION") return "CONDITION BRANCH";
      return node.kind || "NODE";
    }

    drawDropIndicator(context, drop) {
      context.save();
      context.strokeStyle = COLORS.signal;
      context.lineWidth = 3;
      context.shadowColor = "rgba(241,164,49,.32)";
      context.shadowBlur = 8;
      context.beginPath();
      context.moveTo(drop.x, drop.y);
      context.lineTo(drop.x + drop.width, drop.y);
      context.stroke();
      context.fillStyle = COLORS.signal;
      context.beginPath();
      context.arc(drop.x, drop.y, 4, 0, Math.PI * 2);
      context.fill();
      context.restore();
    }

    drawDragGhost(context) {
      const source = this.drag.source;
      this.drawNode(context, source, {
        ghost: true,
        x: source.x,
        y: this.drag.pointer.y - this.drag.offsetY,
        opacity: .82
      });
    }

    pointFromEvent(event) {
      const bounds = this.canvas.getBoundingClientRect();
      return {
        x: (event.clientX - bounds.left) * (this.layout.width / bounds.width),
        y: (event.clientY - bounds.top) * (this.layout.height / bounds.height)
      };
    }

    hitTest(point) {
      return this.layout.nodes.find(entry =>
        point.x >= entry.x && point.x <= entry.x + entry.width &&
        point.y >= entry.y && point.y <= entry.y + entry.height) || null;
    }

    handlePointerDown(event) {
      if (event.button !== 0) return;
      const point = this.pointFromEvent(event);
      const source = this.hitTest(point);
      if (!source) return;
      event.preventDefault();
      this.onSelect(source.path);
      this.canvas.focus({ preventScroll: true });
      if (this.model.locked) return;
      this.canvas.setPointerCapture?.(event.pointerId);
      this.drag = {
        pointerId: event.pointerId,
        source,
        start: point,
        pointer: point,
        offsetY: point.y - source.y,
        active: false,
        invalidTarget: false,
        drop: null
      };
    }

    handlePointerMove(event) {
      const point = this.pointFromEvent(event);
      if (!this.drag) {
        this.canvas.style.cursor = this.hitTest(point) ? (this.model.locked ? "pointer" : "grab") : "default";
        return;
      }
      this.drag.pointer = point;
      const distance = Math.hypot(point.x - this.drag.start.x, point.y - this.drag.start.y);
      if (!this.drag.active && distance < 5) return;
      this.drag.active = true;
      this.canvas.style.cursor = "grabbing";
      const target = this.hitTest(point);
      this.drag.drop = null;
      this.drag.invalidTarget = Boolean(target && target.containerId !== this.drag.source.containerId);
      if (target && !this.drag.invalidTarget) {
        const after = point.y >= target.y + target.height / 2;
        this.drag.drop = {
          insertionIndex: target.index + (after ? 1 : 0),
          x: target.x - 3,
          y: after ? target.y + target.height + 8 : target.y - 8,
          width: target.width + 6
        };
      }
      this.autoScroll(event.clientY);
      this.draw();
    }

    handlePointerUp(event) {
      if (!this.drag) return;
      const completedDrag = this.drag;
      this.canvas.releasePointerCapture?.(event.pointerId);
      this.drag = null;
      this.canvas.style.cursor = "grab";
      if (completedDrag.active && completedDrag.drop) {
        this.onMove(completedDrag.source.path, completedDrag.drop.insertionIndex);
      } else if (completedDrag.active && completedDrag.invalidTarget) {
        this.onNotice("子动作只能在同一循环体或条件分支内调整顺序。", true);
      }
      this.draw();
    }

    supportsExternalAction(event) {
      return Array.from(event.dataTransfer?.types || []).includes(ACTION_CATALOG_MIME);
    }

    handleExternalDragOver(event) {
      if (!this.supportsExternalAction(event)) return;
      event.preventDefault();
      if (this.model.locked) {
        event.dataTransfer.dropEffect = "none";
        return;
      }
      event.dataTransfer.dropEffect = "copy";
      this.externalDrop = this.resolveExternalDrop(this.pointFromEvent(event));
      this.draw();
    }

    resolveExternalDrop(point) {
      const target = this.hitTest(point);
      if (target) {
        const after = point.y >= target.y + target.height / 2;
        const segment = target.path[target.path.length - 1];
        return {
          parentPath: target.path.slice(0, -1).map(item => ({ ...item })),
          containerKey: segment.key,
          insertionIndex: target.index + (after ? 1 : 0),
          x: target.x - 3,
          y: after ? target.y + target.height + 8 : target.y - 8,
          width: target.width + 6
        };
      }
      const rootEntries = this.layout.nodes.filter(entry => entry.containerId === "root/steps");
      const last = rootEntries[rootEntries.length - 1];
      return {
        parentPath: [],
        containerKey: "steps",
        insertionIndex: rootEntries.length,
        x: last?.x - 3 || GEOMETRY.left,
        y: last ? last.y + last.height + 8 : GEOMETRY.top,
        width: last?.width + 6 || Math.max(GEOMETRY.minimumNodeWidth, this.layout.width - GEOMETRY.left * 2)
      };
    }

    handleExternalDrop(event) {
      if (!this.supportsExternalAction(event)) return;
      event.preventDefault();
      const location = this.externalDrop || this.resolveExternalDrop(this.pointFromEvent(event));
      const serializedItem = event.dataTransfer.getData(ACTION_CATALOG_MIME);
      this.externalDrop = null;
      if (this.model.locked) return this.onNotice("已发布版本不可添加组合动作。", true);
      try {
        this.onExternalDrop(JSON.parse(serializedItem), {
          parentPath: location.parentPath,
          containerKey: location.containerKey,
          insertionIndex: location.insertionIndex
        });
      } catch (error) {
        this.onNotice(`无法添加组合动作：${error.message}`, true);
      }
      this.draw();
    }

    clearExternalDrop() {
      if (!this.externalDrop) return;
      this.externalDrop = null;
      this.draw();
    }

    cancelDrag() {
      this.drag = null;
      this.canvas.style.cursor = "default";
      this.draw();
    }

    autoScroll(clientY) {
      const bounds = this.viewport.getBoundingClientRect();
      const edge = 54;
      if (clientY < bounds.top + edge) this.viewport.scrollTop -= 12;
      else if (clientY > bounds.bottom - edge) this.viewport.scrollTop += 12;
    }

    handleKeyboard(event) {
      if (this.layout.nodes.length === 0) return;
      let selectedIndex = this.layout.nodes.findIndex(entry => pathsEqual(entry.path, this.model.selectedPath));
      if (selectedIndex < 0) selectedIndex = 0;
      const selected = this.layout.nodes[selectedIndex];

      if (event.altKey && (event.key === "ArrowUp" || event.key === "ArrowDown")) {
        event.preventDefault();
        if (this.model.locked) return this.onNotice("已发布版本不可调整顺序。", true);
        const delta = event.key === "ArrowUp" ? -1 : 1;
        const targetIndex = selected.index + delta;
        if (targetIndex < 0 || targetIndex >= selected.containerSize) return;
        this.onMove(selected.path, delta < 0 ? selected.index - 1 : selected.index + 2);
        return;
      }

      let nextIndex = selectedIndex;
      if (event.key === "ArrowUp") nextIndex = Math.max(0, selectedIndex - 1);
      else if (event.key === "ArrowDown") nextIndex = Math.min(this.layout.nodes.length - 1, selectedIndex + 1);
      else if (event.key === "Home") nextIndex = 0;
      else if (event.key === "End") nextIndex = this.layout.nodes.length - 1;
      else return;
      event.preventDefault();
      const next = this.layout.nodes[nextIndex];
      this.onSelect(next.path);
      this.ensureVisible(next);
      this.announce(`${next.node.displayName || next.node.stepId}，同级第 ${next.index + 1} 个`);
    }

    ensureVisible(entry) {
      const top = entry.y - 24;
      const bottom = entry.y + entry.height + 24;
      if (top < this.viewport.scrollTop) this.viewport.scrollTop = top;
      else if (bottom > this.viewport.scrollTop + this.viewport.clientHeight)
        this.viewport.scrollTop = bottom - this.viewport.clientHeight;
    }

    announce(message) {
      if (this.liveRegion) this.liveRegion.textContent = message;
    }

    destroy() {
      this.resizeObserver?.disconnect();
    }
  }

  return Object.freeze({ ACTION_CATALOG_MIME, buildWorkflowLayout, WorkflowCanvas });
});

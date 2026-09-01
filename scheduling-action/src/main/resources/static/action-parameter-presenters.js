(function (root, factory) {
  const api = factory(
    typeof module === "object" && module.exports ? require("./action-parameter-editor.js") : root.ActionParameterEditor,
    typeof module === "object" && module.exports ? require("./action-relative-motion.js") : root.ActionRelativeMotion
  );
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionParameterPresenters = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function (ParameterEditor, RelativeMotion) {
  "use strict";

  const FIELD_DESCRIPTIONS = Object.freeze({
    commandId: "32 位十六进制命令标识；JSON 优先，服务端不会替换",
    armCommandModelType: "1 移动 / 2 清除报警 / 3 只读查询位置",
    armMoveRequestType: "1 笛卡尔位姿 / 2 六轴关节位姿",
    armPoseXYZRxRyRz: "笛卡尔坐标与姿态角",
    armPoseJ1J2J3J4J5J6: "六轴关节角",
    speedPercent: "运动速度百分比，协议范围 1-100",
    gripperCommandModelType: "1 移动 / 2 停止 / 3 查询",
    targetWidthPercent: "目标开合宽度百分比，协议范围 0-100",
    forcePercent: "夹持力百分比，协议范围 20-100"
  });

  function normalizeOperation(operation) {
    return String(operation || "").trim().toUpperCase().replace(/\./g, "_");
  }

  function resolve(operation) {
    const normalized = normalizeOperation(operation);
    if (normalized === "MOVE_TO_POSE") return "arm";
    if (normalized === "GRIP" || normalized.indexOf("GRIP_") === 0) return "gripper";
    return "generic";
  }

  function describeField(field) {
    return FIELD_DESCRIPTIONS[field] || "";
  }

  /** operation 仅选择展示器，不改写下游协议中的 operation 原值。 */
  function render(container, operation, initialParameters, options) {
    const settings = Object.assign({ editable: true, onChange: function () {}, onError: function () {} }, options);
    let parameters = clone(initialParameters || {});
    container.innerHTML = "";
    container.className = `parameter-presenter presenter-${resolve(operation)}`;

    function emit(next) {
      parameters = clone(next);
      settings.onChange(clone(next));
      if (genericDetails && genericDetails.open) renderGenericEditor();
    }

    const kind = resolve(operation);
    if (kind === "arm") container.appendChild(renderArmPanel());
    if (kind === "gripper") container.appendChild(renderGripperPanel());

    const genericDetails = document.createElement("details");
    genericDetails.className = "generic-parameter-panel";
    genericDetails.open = kind === "generic";
    const summary = document.createElement("summary");
    summary.innerHTML = kind === "generic"
      ? "<b>通用参数表单</b><small>根据 JSON 类型递归生成</small>"
      : "<b>高级参数表单</b><small>编辑完整原始 JSON 结构</small>";
    genericDetails.appendChild(summary);
    const editorHost = document.createElement("div");
    editorHost.className = "generic-editor-host";
    genericDetails.appendChild(editorHost);
    genericDetails.addEventListener("toggle", () => { if (genericDetails.open) renderGenericEditor(); });
    container.appendChild(genericDetails);
    if (genericDetails.open) renderGenericEditor();

    function renderGenericEditor() {
      ParameterEditor.render(editorHost, parameters, {
        editable: settings.editable,
        describeField,
        onError: settings.onError,
        onChange: next => {
          parameters = clone(next);
          settings.onChange(clone(next));
        }
      });
    }

    function renderArmPanel() {
      const transient = settings.relativeState || createRelativeState();
      if (!transient.offsets) transient.offsets = {};
      if (!transient.offsets.cartesian) transient.offsets.cartesian = RelativeMotion.emptyOffsets("cartesian");
      if (!transient.offsets.joint) transient.offsets.joint = RelativeMotion.emptyOffsets("joint");
      const requestTypePath = ["armMoveRequestParams", "armMoveRequestType"];
      transient.mode = RelativeMotion.modeForRequestType(get(parameters, requestTypePath, 1));

      const section = document.createElement("section");
      section.className = "device-panel arm-panel";
      section.appendChild(panelHeading("ARM / MOVE TO POSE", "当前位置 + 相对偏移 = 绝对目标"));
      const core = document.createElement("div");
      core.className = "device-core-fields";
      core.appendChild(textControl("COMMAND ID", get(parameters, ["commandId"], ""), value => {
        emit(setNested(parameters, ["commandId"], value));
      }));
      core.appendChild(selectControl("命令模型", get(parameters, ["armCommandModelType"], 1), [
        [1, "1 · 移动"], [2, "2 · 清除报警"], [3, "3 · 查询"]
      ], value => emit(setNested(parameters, ["armCommandModelType"], value))));
      core.appendChild(selectControl("位姿模式", get(parameters,
        requestTypePath, 1), [
        [1, "1 · 笛卡尔"], [2, "2 · 六轴关节"]
      ], value => {
        transient.mode = RelativeMotion.modeForRequestType(value);
        emit(setNested(parameters, requestTypePath, value));
        settings.onTransientChange && settings.onTransientChange();
      }));
      core.appendChild(numberControl("速度 %", get(parameters,
        ["armMoveRequestParams", "speedPercent"], 30), 1, 100,
      value => emit(setNested(parameters, ["armMoveRequestParams", "speedPercent"], value))));
      section.appendChild(core);
      section.appendChild(renderRelativePanel(transient, requestTypePath));
      return section;
    }

    function renderRelativePanel(transient, requestTypePath) {
      const panel = document.createElement("div");
      panel.className = "relative-motion";
      const toolbar = document.createElement("div");
      toolbar.className = "relative-toolbar";
      const modes = document.createElement("div");
      modes.className = "segmented-control";
      [["cartesian", "笛卡尔 XYZ / R"], ["joint", "六轴关节 J1-J6"]].forEach(item => {
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = item[1];
        button.className = transient.mode === item[0] ? "active" : "";
        button.disabled = !settings.editable;
        button.addEventListener("click", () => {
          transient.mode = item[0];
          emit(setNested(parameters, requestTypePath, RelativeMotion.requestTypeForMode(item[0])));
          settings.onTransientChange && settings.onTransientChange();
        });
        modes.appendChild(button);
      });
      toolbar.appendChild(modes);
      const probe = document.createElement("button");
      probe.type = "button";
      probe.className = "button button-outline probe-button";
      probe.textContent = transient.probing ? "正在读取…" : "获取当前位置";
      probe.disabled = !settings.editable || !settings.canProbe || transient.probing;
      probe.title = settings.probeDisabledReason || "只读查询，不会触发移动";
      probe.addEventListener("click", async () => {
        transient.probing = true;
        settings.onTransientChange && settings.onTransientChange();
        try { await settings.onProbe(); } catch (error) {
          settings.onError(error);
        } finally {
          transient.probing = false;
          settings.onTransientChange && settings.onTransientChange();
        }
      });
      toolbar.appendChild(probe);
      panel.appendChild(toolbar);

      const meta = document.createElement("p");
      meta.className = "probe-meta";
      meta.textContent = transient.probe
        ? `基准机器人 ${transient.probe.robotId} · ${formatTime(transient.probe.capturedAt)}`
        : "尚未获取基准。当前值和偏移只存在于此页面，不会写入 Action。";
      panel.appendChild(meta);

      const dragHint = document.createElement("p");
      dragHint.className = "relative-drag-hint";
      dragHint.innerHTML = "<b>RELATIVE JOG</b><span>中心为 0；拖动或键盘调节偏移。滑轨是快捷范围，精确输入不受此范围限制。</span>";
      panel.appendChild(dragHint);

      const fields = RelativeMotion.FIELDS[transient.mode];
      const baseline = transient.probe && transient.probe[transient.mode];
      const table = document.createElement("div");
      table.className = "relative-axis-grid";
      const targets = {};
      fields.forEach(field => {
        const axisControl = relativeAxisControl(field, transient.mode, baseline, transient, updateTargets);
        targets[field] = axisControl.target;
        table.appendChild(axisControl.element);
      });
      panel.appendChild(table);

      const apply = document.createElement("button");
      apply.type = "button";
      apply.className = "button button-primary apply-target-button";
      apply.textContent = "应用为绝对目标值";
      apply.disabled = !settings.editable || !baseline;
      apply.addEventListener("click", () => {
        try {
          const target = RelativeMotion.calculate(transient.mode, baseline,
            transient.offsets[transient.mode]);
          emit(RelativeMotion.applyTarget(parameters, transient.mode, target));
          settings.onAppliedTarget && settings.onAppliedTarget(target, transient.mode);
        } catch (error) { settings.onError(error); }
      });
      panel.appendChild(apply);
      updateTargets();
      return panel;

      function updateTargets() {
        let result = null;
        if (baseline) {
          try { result = RelativeMotion.calculate(transient.mode, baseline, transient.offsets[transient.mode]); }
          catch (error) { settings.onError(error); }
        }
        fields.forEach(field => { targets[field].textContent = result ? formatNumber(result[field]) : "—"; });
      }

      function relativeAxisControl(field, mode, currentPose, relativeState, onOffsetChange) {
        const config = RelativeMotion.controlFor(mode, field);
        const card = document.createElement("article");
        card.className = "relative-axis-control";

        const heading = document.createElement("div");
        heading.className = "relative-axis-heading";
        const axis = document.createElement("b");
        axis.innerHTML = `${field.toUpperCase()} <small>${config.unit}</small>`;
        const offsetOutput = document.createElement("output");
        heading.append(axis, offsetOutput);

        const equation = document.createElement("div");
        equation.className = "relative-axis-equation";
        const current = document.createElement("span");
        current.innerHTML = `当前 <output>${currentPose ? formatNumber(currentPose[field]) : "—"}</output>`;
        const arrow = document.createElement("i");
        arrow.textContent = "→";
        const target = document.createElement("output");
        const targetCell = document.createElement("span");
        targetCell.append("目标 ", target);
        equation.append(current, arrow, targetCell);

        const dragRow = document.createElement("div");
        dragRow.className = "relative-drag-row";
        const minLabel = document.createElement("small");
        minLabel.textContent = formatSigned(config.min);
        const rangeShell = document.createElement("div");
        rangeShell.className = "relative-range-shell";
        const range = document.createElement("input");
        range.type = "range";
        range.min = config.min;
        range.max = config.max;
        range.step = config.step;
        range.disabled = !settings.editable;
        range.setAttribute("aria-label", `${field} 相对偏移拖拽`);
        rangeShell.appendChild(range);
        const maxLabel = document.createElement("small");
        maxLabel.textContent = formatSigned(config.max);
        dragRow.append(minLabel, rangeShell, maxLabel);

        const precisionRow = document.createElement("div");
        precisionRow.className = "relative-precision-row";
        const precision = document.createElement("label");
        precision.append("精确偏移");
        const number = document.createElement("input");
        number.type = "number";
        number.step = "any";
        number.disabled = !settings.editable;
        number.setAttribute("aria-label", `${field} 精确相对偏移`);
        precision.appendChild(number);
        const reset = document.createElement("button");
        reset.type = "button";
        reset.className = "relative-zero-button";
        reset.textContent = "归零";
        reset.disabled = !settings.editable;
        reset.setAttribute("aria-label", `${field} 偏移归零`);
        precisionRow.append(precision, reset);

        const updateOffset = (value, notify = true) => {
          relativeState.offsets[mode][field] = value;
          number.value = value;
          range.value = RelativeMotion.clampToControl(mode, field, value);
          const outsideRange = value < config.min || value > config.max;
          card.classList.toggle("is-outside-range", outsideRange);
          offsetOutput.textContent = `${formatSigned(value)} ${config.unit}`;
          offsetOutput.dataset.polarity = value < 0 ? "negative" : value > 0 ? "positive" : "zero";
          paintCenteredRange(range, Number(range.value), config);
          if (notify) onOffsetChange();
        };
        range.addEventListener("input", () => updateOffset(Number(range.value)));
        number.addEventListener("input", () => {
          if (number.value !== "" && Number.isFinite(Number(number.value))) updateOffset(Number(number.value));
        });
        number.addEventListener("change", () => {
          if (number.value === "" || !Number.isFinite(Number(number.value))) {
            updateOffset(relativeState.offsets[mode][field]);
          }
        });
        reset.addEventListener("click", () => updateOffset(0));

        card.append(heading, equation, dragRow, precisionRow);
        updateOffset(Number(relativeState.offsets[mode][field]) || 0, false);
        return { element: card, target };
      }
    }

    function renderGripperPanel() {
      const section = document.createElement("section");
      section.className = "device-panel gripper-panel";
      section.appendChild(panelHeading("GRIPPER / END EFFECTOR", gripperHint(operation)));
      const core = document.createElement("div");
      core.className = "device-core-fields";
      core.appendChild(textControl("COMMAND ID", get(parameters, ["commandId"], ""),
        value => emit(setNested(parameters, ["commandId"], value))));
      core.appendChild(selectControl("命令模型", get(parameters, ["gripperCommandModelType"], 1), [
        [1, "1 · 移动"], [2, "2 · 停止"], [3, "3 · 查询"]
      ], value => emit(setNested(parameters, ["gripperCommandModelType"], value))));
      section.appendChild(core);
      const rails = document.createElement("div");
      rails.className = "gripper-rails";
      [
        ["targetWidthPercent", "开合宽度", 0, 100],
        ["forcePercent", "夹持力", 20, 100],
        ["speedPercent", "速度", 1, 100]
      ].forEach(item => rails.appendChild(percentControl(item[1],
        get(parameters, ["gripperMoveRequestParams", item[0]], item[2]), item[2], item[3], value => {
          emit(setNested(parameters, ["gripperMoveRequestParams", item[0]], value));
        })));
      section.appendChild(rails);
      return section;
    }

    function textControl(label, value, onChange) {
      const wrapper = control(label);
      const input = document.createElement("input");
      input.type = "text";
      input.value = value == null ? "" : value;
      input.disabled = !settings.editable;
      input.addEventListener("change", () => onChange(input.value));
      wrapper.appendChild(input);
      return wrapper;
    }

    function numberControl(label, value, min, max, onChange) {
      const wrapper = control(label);
      const input = document.createElement("input");
      input.type = "number";
      input.min = min;
      input.max = max;
      input.value = value;
      input.disabled = !settings.editable;
      input.addEventListener("change", () => onChange(Number(input.value)));
      wrapper.appendChild(input);
      return wrapper;
    }

    function selectControl(label, value, values, onChange) {
      const wrapper = control(label);
      const select = document.createElement("select");
      values.forEach(item => select.add(new Option(item[1], item[0])));
      select.value = value;
      select.disabled = !settings.editable;
      select.addEventListener("change", () => onChange(Number(select.value)));
      wrapper.appendChild(select);
      return wrapper;
    }

    function percentControl(label, value, min, max, onChange) {
      const wrapper = document.createElement("label");
      wrapper.className = "percent-control";
      const header = document.createElement("span");
      header.innerHTML = `<b>${label}</b><output>${value}%</output>`;
      const range = document.createElement("input");
      range.type = "range";
      range.min = min;
      range.max = max;
      range.value = value;
      range.disabled = !settings.editable;
      range.addEventListener("input", () => {
        header.querySelector("output").textContent = `${range.value}%`;
        onChange(Number(range.value));
      });
      wrapper.append(header, range);
      return wrapper;
    }

    function control(label) {
      const wrapper = document.createElement("label");
      wrapper.className = "quick-control";
      const span = document.createElement("span");
      span.textContent = label;
      wrapper.appendChild(span);
      return wrapper;
    }
  }

  function createRelativeState() {
    return RelativeMotion.createTransientState();
  }

  function setNested(source, path, value) {
    const result = clone(source || {});
    let cursor = result;
    path.slice(0, -1).forEach(key => {
      if (!cursor[key] || typeof cursor[key] !== "object" || Array.isArray(cursor[key])) cursor[key] = {};
      cursor = cursor[key];
    });
    cursor[path[path.length - 1]] = value;
    return result;
  }

  function get(source, path, fallback) {
    const value = path.reduce((cursor, key) => cursor == null ? undefined : cursor[key], source);
    return value === undefined || value === null ? fallback : value;
  }

  function panelHeading(code, description) {
    const heading = document.createElement("div");
    heading.className = "device-panel-heading";
    const strong = document.createElement("strong");
    strong.textContent = code;
    const span = document.createElement("span");
    span.textContent = description;
    heading.append(strong, span);
    return heading;
  }

  function gripperHint(operation) {
    const normalized = normalizeOperation(operation);
    if (normalized === "GRIP_OPEN") return "开启操作 · 目标宽度通常接近 100%";
    if (normalized === "GRIP_CLOSE") return "闭合操作 · 请同时核对夹持力";
    if (normalized === "GRIP_VERIFY_LOAD") return "负载确认 · 保留下游注册的原始 operation";
    return "夹爪通用线协议参数";
  }

  function formatNumber(value) {
    return Number.isInteger(value) ? String(value) : Number(value).toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
  }

  function formatSigned(value) {
    const number = Number(value);
    return number > 0 ? `+${formatNumber(number)}` : formatNumber(number);
  }

  function paintCenteredRange(range, value, config) {
    const position = ((value - config.min) / (config.max - config.min)) * 100;
    const zero = ((0 - config.min) / (config.max - config.min)) * 100;
    range.style.setProperty("--active-start", `${Math.min(position, zero)}%`);
    range.style.setProperty("--active-end", `${Math.max(position, zero)}%`);
    range.style.setProperty("--offset-color", value < 0 ? "#d07835" : "#087f72");
  }

  function formatTime(value) {
    if (!value) return "获取时间未知";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  return { normalizeOperation, resolve, describeField, createRelativeState, render };
});

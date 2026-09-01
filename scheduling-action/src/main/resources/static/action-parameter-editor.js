(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionParameterEditor = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const TYPES = Object.freeze(["string", "number", "boolean", "object", "array", "null"]);
  const COMMAND_ID_PATTERN = /^[0-9a-fA-F]{32}$/;

  function valueType(value) {
    if (value === null) return "null";
    if (Array.isArray(value)) return "array";
    return typeof value === "number" ? "number"
      : typeof value === "boolean" ? "boolean"
        : typeof value === "object" ? "object" : "string";
  }

  function setAtPath(source, path, value) {
    if (!Array.isArray(path)) throw new Error("参数路径必须是数组。");
    if (!path.length) return clone(value);
    const result = clone(source);
    let cursor = result;
    for (let index = 0; index < path.length - 1; index += 1) {
      const key = path[index];
      if (!cursor || typeof cursor !== "object") throw new Error("参数路径不存在。");
      cursor = cursor[key];
    }
    if (!cursor || typeof cursor !== "object") throw new Error("参数路径不存在。");
    cursor[path[path.length - 1]] = clone(value);
    return result;
  }

  function removeAtPath(source, path) {
    if (!Array.isArray(path) || !path.length) throw new Error("不能删除参数根节点。");
    const result = clone(source);
    const parent = getAtPath(result, path.slice(0, -1));
    const key = path[path.length - 1];
    if (Array.isArray(parent)) parent.splice(Number(key), 1);
    else if (parent && typeof parent === "object") delete parent[key];
    else throw new Error("参数路径不存在。");
    return result;
  }

  function renameAtPath(source, path, requestedName) {
    if (!Array.isArray(path) || !path.length) throw new Error("根节点不能重命名。");
    const name = String(requestedName == null ? "" : requestedName).trim();
    if (!name) throw new Error("字段名不能为空。");
    const result = clone(source);
    const parent = getAtPath(result, path.slice(0, -1));
    const oldName = String(path[path.length - 1]);
    if (!parent || typeof parent !== "object" || Array.isArray(parent)) {
      throw new Error("只能重命名对象字段。");
    }
    if (name !== oldName && Object.prototype.hasOwnProperty.call(parent, name)) {
      throw new Error(`同一层级已存在字段“${name}”。`);
    }
    if (name === oldName) return result;
    const reordered = {};
    Object.keys(parent).forEach(key => { reordered[key === oldName ? name : key] = parent[key]; });
    const parentPath = path.slice(0, -1);
    return parentPath.length ? setAtPath(result, parentPath, reordered) : reordered;
  }

  function addProperty(source, objectPath, requestedName, value) {
    const result = clone(source);
    const parent = getAtPath(result, objectPath || []);
    if (!parent || typeof parent !== "object" || Array.isArray(parent)) {
      throw new Error("只能向 JSON 对象添加字段。");
    }
    const name = String(requestedName == null ? nextFieldName(parent) : requestedName).trim();
    if (!name) throw new Error("字段名不能为空。");
    if (Object.prototype.hasOwnProperty.call(parent, name)) {
      throw new Error(`同一层级已存在字段“${name}”。`);
    }
    parent[name] = value === undefined ? "" : clone(value);
    return result;
  }

  function addArrayItem(source, arrayPath, value) {
    const result = clone(source);
    const parent = getAtPath(result, arrayPath || []);
    if (!Array.isArray(parent)) throw new Error("只能向 JSON 数组添加元素。");
    parent.push(value === undefined ? "" : clone(value));
    return result;
  }

  function convertAtPath(source, path, type) {
    if (!TYPES.includes(type)) throw new Error("不支持的 JSON 类型。");
    const current = getAtPath(source, path || []);
    return setAtPath(source, path || [], convertValue(current, type));
  }

  function commandIdIssues(definition) {
    const occurrences = new Map();
    const issues = [];
    walk(definition, [], (value, path, key) => {
      if (key !== "commandId") return;
      const location = formatPath(path);
      if (typeof value !== "string" || !COMMAND_ID_PATTERN.test(value)) {
        issues.push({ type: "invalid", value, path: location,
          message: `${location} 必须是 32 位十六进制字符串。` });
        return;
      }
      const normalized = value.toLowerCase();
      const paths = occurrences.get(normalized) || [];
      paths.push(location);
      occurrences.set(normalized, paths);
    });
    occurrences.forEach((paths, id) => {
      if (paths.length > 1) issues.push({ type: "duplicate", value: id, paths,
        message: `commandId ${id} 在当前 Action 内重复：${paths.join("、")}` });
    });
    return issues;
  }

  /** 根据当前 JSON 值递归渲染，不依赖任何参数 Schema。 */
  function render(container, initialValue, options) {
    const settings = Object.assign({ editable: true, onChange: function () {}, onError: function () {} }, options);
    let value = clone(initialValue == null ? null : initialValue);
    container.innerHTML = "";
    container.classList.add("recursive-editor");
    container.appendChild(renderNode(value, [], null, true));

    function commit(next) {
      value = next;
      settings.onChange(clone(next));
      container.innerHTML = "";
      container.appendChild(renderNode(value, [], null, true));
    }

    function attempt(change) {
      try { commit(change()); } catch (error) {
        settings.onError(error);
        container.innerHTML = "";
        container.appendChild(renderNode(value, [], null, true));
      }
    }

    function renderNode(nodeValue, path, propertyName, isRoot) {
      const type = valueType(nodeValue);
      const wrapper = document.createElement("div");
      wrapper.className = `json-node json-node-${type}${isRoot ? " json-node-root" : ""}`;

      const row = document.createElement("div");
      row.className = "json-node-row";
      if (!isRoot) {
        if (typeof path[path.length - 1] === "number") {
          const index = document.createElement("span");
          index.className = "json-array-index";
          index.textContent = `[${path[path.length - 1]}]`;
          row.appendChild(index);
        } else {
          const key = document.createElement("input");
          key.className = "json-key";
          key.value = propertyName;
          key.disabled = !settings.editable;
          key.setAttribute("aria-label", "JSON 字段名");
          key.addEventListener("change", () => attempt(() => renameAtPath(value, path, key.value)));
          row.appendChild(key);
        }
      } else {
        const rootLabel = document.createElement("span");
        rootLabel.className = "json-root-label";
        rootLabel.textContent = "PARAMETERS";
        row.appendChild(rootLabel);
      }

      const typeSelect = document.createElement("select");
      typeSelect.className = "json-type";
      // parameters 根节点在 Action 2.0 中固定为对象，子节点仍可自由切换 JSON 类型。
      typeSelect.disabled = !settings.editable || isRoot;
      TYPES.forEach(candidate => typeSelect.add(new Option(candidate.toUpperCase(), candidate)));
      typeSelect.value = type;
      typeSelect.addEventListener("change", () => attempt(() => convertAtPath(value, path, typeSelect.value)));
      row.appendChild(typeSelect);

      if (type === "string" || type === "number" || type === "boolean") {
        row.appendChild(renderPrimitive(nodeValue, path, type));
      } else if (type === "null") {
        const nullValue = document.createElement("code");
        nullValue.className = "json-null";
        nullValue.textContent = "null";
        row.appendChild(nullValue);
      } else {
        const count = document.createElement("span");
        count.className = "json-child-count";
        count.textContent = type === "array" ? `${nodeValue.length} 项` : `${Object.keys(nodeValue).length} 个字段`;
        row.appendChild(count);
      }

      if (!isRoot) {
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "json-remove";
        remove.textContent = "删除";
        remove.disabled = !settings.editable;
        remove.addEventListener("click", () => attempt(() => removeAtPath(value, path)));
        row.appendChild(remove);
      }
      wrapper.appendChild(row);

      if (settings.describeField && propertyName) {
        const description = settings.describeField(propertyName);
        if (description) {
          const hint = document.createElement("small");
          hint.className = "json-field-hint";
          hint.textContent = description;
          wrapper.appendChild(hint);
        }
      }

      if (type === "object" || type === "array") {
        const children = document.createElement("div");
        children.className = "json-children";
        const entries = type === "array" ? nodeValue.map((item, index) => [index, item]) : Object.entries(nodeValue);
        entries.forEach(entry => children.appendChild(renderNode(
          entry[1], path.concat(entry[0]), type === "object" ? entry[0] : null, false)));
        wrapper.appendChild(children);
        const add = document.createElement("button");
        add.type = "button";
        add.className = "json-add";
        add.disabled = !settings.editable;
        add.textContent = type === "object" ? "＋ 添加字段" : "＋ 添加数组项";
        add.addEventListener("click", () => attempt(() => type === "object"
          ? addProperty(value, path) : addArrayItem(value, path)));
        wrapper.appendChild(add);
      }
      return wrapper;
    }

    function renderPrimitive(nodeValue, path, type) {
      if (type === "boolean") {
        const input = document.createElement("input");
        input.type = "checkbox";
        input.className = "json-boolean";
        input.checked = nodeValue;
        input.disabled = !settings.editable;
        input.addEventListener("change", () => attempt(() => setAtPath(value, path, input.checked)));
        return input;
      }
      const input = document.createElement("input");
      input.type = type === "number" ? "number" : "text";
      input.className = "json-value";
      input.value = nodeValue;
      input.disabled = !settings.editable;
      input.addEventListener("change", () => attempt(() => {
        if (type === "number") {
          const number = Number(input.value);
          if (!Number.isFinite(number)) throw new Error("数字字段必须是有限值。");
          return setAtPath(value, path, number);
        }
        return setAtPath(value, path, input.value);
      }));
      return input;
    }
  }

  function getAtPath(source, path) {
    return (path || []).reduce((value, key) => value == null ? undefined : value[key], source);
  }

  function convertValue(value, type) {
    if (type === "null") return null;
    if (type === "object") return {};
    if (type === "array") return [];
    if (type === "boolean") return value === true || value === "true" || value === 1;
    if (type === "number") {
      const number = Number(value);
      return Number.isFinite(number) ? number : 0;
    }
    return value == null ? "" : String(value);
  }

  function nextFieldName(object) {
    if (!Object.prototype.hasOwnProperty.call(object, "newField")) return "newField";
    let index = 2;
    while (Object.prototype.hasOwnProperty.call(object, `newField${index}`)) index += 1;
    return `newField${index}`;
  }

  function walk(value, path, visitor) {
    if (!value || typeof value !== "object") return;
    Object.keys(value).forEach(key => {
      const childPath = path.concat(Array.isArray(value) ? Number(key) : key);
      visitor(value[key], childPath, key);
      walk(value[key], childPath, visitor);
    });
  }

  function formatPath(path) {
    return path.reduce((text, part) => typeof part === "number" ? `${text}[${part}]` : `${text}.${part}`, "$");
  }

  function clone(value) {
    if (value === undefined) return undefined;
    return JSON.parse(JSON.stringify(value));
  }

  return {
    TYPES, COMMAND_ID_PATTERN, valueType, setAtPath, removeAtPath, renameAtPath,
    addProperty, addArrayItem, convertAtPath, commandIdIssues, render
  };
});

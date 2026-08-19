(function exposeActionReference(root, factory) {
  const ordering = typeof module === "object" && module.exports
    ? require("./action-ordering.js")
    : root.ActionOrdering;
  const api = factory(ordering);
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionReference = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionReferenceApi(ActionOrdering) {
  "use strict";

  function listCatalogItems(items, { scope = "", currentActionKey = "", query = "" } = {}) {
    const normalizedQuery = query.trim().toLocaleLowerCase("zh-CN");
    return (items || []).filter(item => {
      // Only non-entry actions are global reusable composites. Entry actions are
      // scheduling entry points and must never appear in the reference library.
      if (item.entryPoint === true) return false;
      if (item.status && item.status !== "PUBLISHED") return false;
      if (scope && item.scope !== scope) return false;
      if (currentActionKey && item.actionKey === currentActionKey) return false;
      if (!normalizedQuery) return true;
      const searchable = [item.displayName, item.actionKey, item.description, ...Object.values(item.labels || {})]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase("zh-CN");
      return searchable.includes(normalizedQuery);
    });
  }

  function createActionReference(catalogItem, ownerDefinition, existingStepIds = []) {
    if (!catalogItem?.actionKey || !catalogItem?.version)
      throw new TypeError("A published actionKey and exact version are required.");
    if (catalogItem.entryPoint === true)
      throw new TypeError("An entry action cannot be used as a global composite reference.");
    if (catalogItem.status && catalogItem.status !== "PUBLISHED")
      throw new TypeError("Only a published global composite can be referenced.");
    const bindings = {};
    Object.entries(catalogItem.inputSchema || {}).forEach(([name, targetSchema]) => {
      const sourceSchema = ownerDefinition?.inputSchema?.[name];
      if (sourceSchema && areTypesCompatible(sourceSchema.type, targetSchema.type))
        bindings[name] = `$input.${name}`;
    });
    return {
      kind: "ACTION_REF",
      stepId: uniqueStepId(catalogItem.actionKey, existingStepIds),
      displayName: catalogItem.displayName || catalogItem.actionKey,
      description: catalogItem.description || "",
      enabled: true,
      timeoutMs: catalogItem.defaultTimeoutMs || 60000,
      onFailure: { strategy: "ABORT", maxRetries: 0 },
      gate: Boolean(catalogItem.hasPhysicalSideEffect),
      outputs: {},
      actionRef: { actionKey: catalogItem.actionKey, version: catalogItem.version },
      with: bindings
    };
  }

  function areTypesCompatible(sourceType, targetType) {
    return sourceType === targetType || sourceType === "INTEGER" && targetType === "NUMBER";
  }

  function uniqueStepId(actionKey, existingStepIds) {
    const seed = actionKey.toLocaleLowerCase("en-US").replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "") || "action";
    const used = new Set(existingStepIds || []);
    let candidate = seed;
    let suffix = 2;
    while (used.has(candidate)) candidate = `${seed}_${suffix++}`;
    return candidate;
  }

  function promoteReferenceInput(definition, nodePath, catalogItem, parameterName) {
    const targetSchema = catalogItem?.inputSchema?.[parameterName];
    if (!targetSchema) throw new RangeError(`Parameter '${parameterName}' is not declared by the referenced action.`);
    const node = ActionOrdering.getNodeAtPath(definition, nodePath);
    assertMatchingReference(node, catalogItem);
    const existingSchema = definition.inputSchema?.[parameterName];
    if (existingSchema && !areTypesCompatible(existingSchema.type, targetSchema.type))
      throw new TypeError(`Parent input '${parameterName}' has incompatible type ${existingSchema.type}.`);
    const nextNode = {
      ...node,
      with: { ...(node.with || {}), [parameterName]: `$input.${parameterName}` }
    };
    const definitionWithBinding = ActionOrdering.replaceNodeAtPath(definition, nodePath, nextNode);
    return {
      ...definitionWithBinding,
      inputSchema: {
        ...(definition.inputSchema || {}),
        [parameterName]: existingSchema || JSON.parse(JSON.stringify(targetSchema))
      }
    };
  }

  function updateReferenceBinding(definition, nodePath, parameterName, value) {
    if (!parameterName) throw new TypeError("parameterName is required.");
    const node = ActionOrdering.getNodeAtPath(definition, nodePath);
    if (node?.kind !== "ACTION_REF") throw new TypeError("The selected node is not an action reference.");
    const bindings = { ...(node.with || {}) };
    if (value === undefined) delete bindings[parameterName];
    else bindings[parameterName] = value;
    return ActionOrdering.replaceNodeAtPath(definition, nodePath, { ...node, with: bindings });
  }

  function parseBindingValue(rawValue, schema, mode) {
    if (mode === "UNBOUND") return undefined;
    const text = String(rawValue ?? "").trim();
    if (mode === "EXPRESSION") {
      if (!text.startsWith("$")) throw new TypeError("Expression bindings must start with '$'.");
      return text;
    }
    if (mode !== "LITERAL") throw new TypeError(`Unsupported binding mode '${mode}'.`);

    switch (schema?.type) {
      case "STRING":
        if (schema.enumValues?.length && !schema.enumValues.includes(String(rawValue ?? "")))
          throw new RangeError("String value is outside enumValues.");
        return String(rawValue ?? "");
      case "NUMBER": {
        if (!text) throw new TypeError("NUMBER binding cannot be empty.");
        const number = Number(text);
        if (!Number.isFinite(number)) throw new TypeError("NUMBER binding must be a finite number.");
        return number;
      }
      case "INTEGER": {
        if (!text) throw new TypeError("INTEGER binding cannot be empty.");
        const integer = Number(text);
        if (!Number.isInteger(integer)) throw new TypeError("INTEGER binding must be an integer.");
        return integer;
      }
      case "BOOLEAN":
        if (text !== "true" && text !== "false") throw new TypeError("BOOLEAN binding must be true or false.");
        return text === "true";
      case "OBJECT":
      case "ARRAY": {
        let parsed;
        try { parsed = JSON.parse(text); }
        catch (error) { throw new TypeError(`Invalid JSON literal: ${error.message}`); }
        const valid = schema.type === "ARRAY"
          ? Array.isArray(parsed)
          : parsed !== null && typeof parsed === "object" && !Array.isArray(parsed);
        if (!valid) throw new TypeError(`${schema.type} binding has the wrong JSON shape.`);
        return parsed;
      }
      default:
        throw new TypeError(`Unsupported parameter type '${schema?.type}'.`);
    }
  }

  function getMissingRequiredBindings(node, catalogItem) {
    const bindings = node?.with || {};
    return Object.entries(catalogItem?.inputSchema || {})
      .filter(([name, schema]) => schema.required &&
        (!Object.prototype.hasOwnProperty.call(bindings, name) || bindings[name] === undefined))
      .map(([name]) => name);
  }

  function assertMatchingReference(node, catalogItem) {
    if (node?.kind !== "ACTION_REF" ||
        node.actionRef?.actionKey !== catalogItem?.actionKey ||
        node.actionRef?.version !== catalogItem?.version)
      throw new TypeError("The selected node does not match the referenced catalog action.");
  }

  return Object.freeze({
    createActionReference,
    getMissingRequiredBindings,
    listCatalogItems,
    parseBindingValue,
    promoteReferenceInput,
    updateReferenceBinding
  });
});

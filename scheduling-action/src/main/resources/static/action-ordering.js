(function exposeActionOrdering(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionOrdering = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionOrdering() {
  "use strict";

  /**
   * Moves one item to a visual insertion point. insertionIndex represents a gap
   * between items, so its valid range is 0..items.length.
   */
  function moveItemAtInsertion(items, fromIndex, insertionIndex) {
    if (!Array.isArray(items)) throw new TypeError("items must be an array.");
    if (!Number.isInteger(fromIndex) || fromIndex < 0 || fromIndex >= items.length)
      throw new RangeError("fromIndex is outside the item range.");
    if (!Number.isInteger(insertionIndex) || insertionIndex < 0 || insertionIndex > items.length)
      throw new RangeError("insertionIndex is outside the available gaps.");

    const toIndex = insertionIndex > fromIndex ? insertionIndex - 1 : insertionIndex;
    if (toIndex === fromIndex) return { items: [...items], toIndex, changed: false };

    const reordered = [...items];
    const [moved] = reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, moved);
    return { items: reordered, toIndex, changed: true };
  }

  const containerKeys = new Set(["steps", "then", "else"]);

  function assertPath(path) {
    if (!Array.isArray(path) || path.length === 0) throw new TypeError("nodePath must be a non-empty array.");
    path.forEach((segment, depth) => {
      if (!segment || !containerKeys.has(segment.key))
        throw new TypeError(`nodePath[${depth}].key is not a supported step container.`);
      if (!Number.isInteger(segment.index) || segment.index < 0)
        throw new RangeError(`nodePath[${depth}].index must be a non-negative integer.`);
    });
  }

  function getNodeAtPath(definition, nodePath) {
    assertPath(nodePath);
    let owner = definition;
    for (const segment of nodePath) {
      const container = owner?.[segment.key];
      if (!Array.isArray(container) || segment.index >= container.length)
        throw new RangeError(`Node path ${pathKey(nodePath)} does not exist.`);
      owner = container[segment.index];
    }
    return owner;
  }

  function getNodeLocation(definition, nodePath) {
    assertPath(nodePath);
    const parentPath = nodePath.slice(0, -1);
    const segment = nodePath[nodePath.length - 1];
    const owner = parentPath.length ? getNodeAtPath(definition, parentPath) : definition;
    const siblings = owner?.[segment.key];
    if (!Array.isArray(siblings) || segment.index >= siblings.length)
      throw new RangeError(`Node path ${pathKey(nodePath)} does not exist.`);
    return { node: siblings[segment.index], siblings, index: segment.index, containerKey: segment.key, parentPath };
  }

  /**
   * Immutably replaces one child container while preserving unrelated branches.
   * parentPath points to the node that owns containerKey; an empty path means the
   * Action definition itself owns the container.
   */
  function updateContainer(definition, parentPath, containerKey, transform, depth = 0) {
    if (depth === parentPath.length) {
      const container = definition?.[containerKey];
      if (!Array.isArray(container)) throw new TypeError(`${containerKey} must be an array.`);
      const nextContainer = transform(container);
      return nextContainer === container ? definition : { ...definition, [containerKey]: nextContainer };
    }

    const segment = parentPath[depth];
    const container = definition?.[segment.key];
    if (!Array.isArray(container) || segment.index >= container.length)
      throw new RangeError(`Parent path ${pathKey(parentPath)} does not exist.`);
    const nextChild = updateContainer(container[segment.index], parentPath, containerKey, transform, depth + 1);
    if (nextChild === container[segment.index]) return definition;
    const nextContainer = [...container];
    nextContainer[segment.index] = nextChild;
    return { ...definition, [segment.key]: nextContainer };
  }

  function moveNodeAtInsertion(definition, nodePath, insertionIndex) {
    const location = getNodeLocation(definition, nodePath);
    const result = moveItemAtInsertion(location.siblings, location.index, insertionIndex);
    const nextDefinition = result.changed
      ? updateContainer(definition, location.parentPath, location.containerKey, () => result.items)
      : definition;
    const nextPath = nodePath.map(segment => ({ ...segment }));
    nextPath[nextPath.length - 1].index = result.toIndex;
    return { definition: nextDefinition, nodePath: nextPath, changed: result.changed, toIndex: result.toIndex };
  }

  function insertNodeAtLocation(definition, location, node) {
    if (!node || typeof node !== "object" || Array.isArray(node)) throw new TypeError("node must be an object.");
    const parentPath = location?.parentPath || [];
    const containerKey = location?.containerKey;
    const insertionIndex = location?.insertionIndex;
    if (!Array.isArray(parentPath)) throw new TypeError("parentPath must be an array.");
    if (parentPath.length) {
      assertPath(parentPath);
      getNodeAtPath(definition, parentPath);
    }
    if (!containerKeys.has(containerKey)) throw new TypeError("containerKey is not a supported step container.");
    const owner = parentPath.length ? getNodeAtPath(definition, parentPath) : definition;
    const container = owner?.[containerKey];
    if (!Array.isArray(container)) throw new TypeError(`${containerKey} must be an array.`);
    if (!Number.isInteger(insertionIndex) || insertionIndex < 0 || insertionIndex > container.length)
      throw new RangeError("insertionIndex is outside the available gaps.");
    const nextDefinition = updateContainer(definition, parentPath, containerKey, siblings => {
      const nextSiblings = [...siblings];
      nextSiblings.splice(insertionIndex, 0, node);
      return nextSiblings;
    });
    return {
      definition: nextDefinition,
      nodePath: [...parentPath.map(segment => ({ ...segment })), { key: containerKey, index: insertionIndex }]
    };
  }

  function replaceNodeAtPath(definition, nodePath, replacement) {
    if (!replacement || typeof replacement !== "object" || Array.isArray(replacement))
      throw new TypeError("replacement must be a node object.");
    const location = getNodeLocation(definition, nodePath);
    return updateContainer(definition, location.parentPath, location.containerKey, siblings => {
      const nextSiblings = [...siblings];
      nextSiblings[location.index] = replacement;
      return nextSiblings;
    });
  }

  function removeNodeAtPath(definition, nodePath) {
    const location = getNodeLocation(definition, nodePath);
    const nextDefinition = updateContainer(definition, location.parentPath, location.containerKey, siblings =>
      siblings.filter((_, index) => index !== location.index));
    return { definition: nextDefinition, removedNode: location.node };
  }

  function areSiblingPaths(leftPath, rightPath) {
    assertPath(leftPath);
    assertPath(rightPath);
    if (leftPath.length !== rightPath.length) return false;
    return leftPath.every((segment, depth) => {
      const other = rightPath[depth];
      return segment.key === other.key && (depth === leftPath.length - 1 || segment.index === other.index);
    });
  }

  function pathsEqual(leftPath, rightPath) {
    if (!Array.isArray(leftPath) || !Array.isArray(rightPath) || leftPath.length !== rightPath.length) return false;
    return leftPath.every((segment, depth) => segment.key === rightPath[depth]?.key && segment.index === rightPath[depth]?.index);
  }

  function pathKey(nodePath) {
    if (!Array.isArray(nodePath)) return "";
    return nodePath.map(segment => `${segment.key}:${segment.index}`).join("/");
  }

  return Object.freeze({
    areSiblingPaths,
    getNodeAtPath,
    getNodeLocation,
    insertNodeAtLocation,
    moveItemAtInsertion,
    moveNodeAtInsertion,
    pathKey,
    pathsEqual,
    removeNodeAtPath,
    replaceNodeAtPath
  });
});

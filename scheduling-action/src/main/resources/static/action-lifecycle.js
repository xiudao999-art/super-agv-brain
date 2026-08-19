(function exposeActionLifecycle(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionLifecycle = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionLifecycleApi() {
  "use strict";

  function describeActionLifecycle({ draft = null, release = null } = {}) {
    if (!draft) return Object.freeze({
      state: "UNSAVED",
      statusLabel: "未保存动作",
      canEdit: true,
      canDeleteDraft: false,
      canCreateVersion: false,
      canDeprecateRelease: false
    });
    if (draft.status === "DRAFT") return Object.freeze({
      state: "DRAFT",
      statusLabel: "可编辑草稿",
      canEdit: true,
      canDeleteDraft: true,
      canCreateVersion: false,
      canDeprecateRelease: false
    });
    if (release?.status === "DEPRECATED") return Object.freeze({
      state: "DEPRECATED",
      statusLabel: "已下线版本",
      canEdit: false,
      canDeleteDraft: false,
      canCreateVersion: true,
      canDeprecateRelease: false
    });
    return Object.freeze({
      state: "PUBLISHED",
      statusLabel: "已发布版本",
      canEdit: false,
      canDeleteDraft: false,
      canCreateVersion: true,
      canDeprecateRelease: true
    });
  }

  function nextAvailablePatchVersion(sourceVersion, usedVersions = []) {
    const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(String(sourceVersion || ""));
    if (!match) throw new TypeError("Source version must be a three-part semantic version.");
    const used = new Set(usedVersions || []);
    const major = Number(match[1]);
    const minor = Number(match[2]);
    let patch = Number(match[3]) + 1;
    let candidate = `${major}.${minor}.${patch}`;
    while (used.has(candidate)) candidate = `${major}.${minor}.${++patch}`;
    return candidate;
  }

  return Object.freeze({ describeActionLifecycle, nextAvailablePatchVersion });
});

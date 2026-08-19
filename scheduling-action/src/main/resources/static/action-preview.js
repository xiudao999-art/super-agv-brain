(function exposeActionPreview(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.ActionPreview = api;
})(typeof globalThis === "object" ? globalThis : window, function createActionPreviewApi() {
  "use strict";

  function isReleasePreview(draft) {
    return draft?.source === "RELEASE_VIEW";
  }

  function catalogExclusionKey(draft, definition) {
    return isReleasePreview(draft) ? "" : definition?.actionKey || "";
  }

  function listMainActionDrafts(drafts) {
    return (drafts || []).filter(draft => draft?.definition?.entryPoint === true);
  }

  function listCompositeDrafts(drafts) {
    return (drafts || []).filter(draft =>
      draft?.definition?.entryPoint === false && draft.status === "DRAFT");
  }

  function preservePreviewOrigin(draft, existingOrigin) {
    if (existingOrigin) return existingOrigin;
    return Object.freeze({ draftId: isReleasePreview(draft) ? null : draft?.id || null });
  }

  return Object.freeze({
    catalogExclusionKey,
    isReleasePreview,
    listCompositeDrafts,
    listMainActionDrafts,
    preservePreviewOrigin
  });
});

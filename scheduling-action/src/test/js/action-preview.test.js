const test = require("node:test");
const assert = require("node:assert/strict");

const {
  catalogExclusionKey,
  listCompositeDrafts,
  listMainActionDrafts,
  preservePreviewOrigin
} = require("../../main/resources/static/action-preview.js");

test("the main Action selector excludes every global composite record", () => {
  const drafts = [
    { id: "main", definition: { entryPoint: true } },
    { id: "composite-draft", definition: { entryPoint: false }, status: "DRAFT" },
    { id: "composite-release", definition: { entryPoint: false }, status: "PUBLISHED" }
  ];

  assert.deepEqual(listMainActionDrafts(drafts).map(item => item.id), ["main"]);
});

test("global composite management exposes editable composite drafts without duplicating releases", () => {
  const drafts = [
    { id: "main", definition: { entryPoint: true }, status: "DRAFT" },
    { id: "composite-draft", definition: { entryPoint: false }, status: "DRAFT" },
    { id: "composite-release", definition: { entryPoint: false }, status: "PUBLISHED" }
  ];

  assert.deepEqual(listCompositeDrafts(drafts).map(item => item.id), ["composite-draft"]);
});

test("a release preview keeps the viewed global composite in the catalog", () => {
  const definition = { actionKey: "QA.SAFE_HOME" };

  assert.equal(catalogExclusionKey({ source: "RELEASE_VIEW" }, definition), "");
  assert.equal(catalogExclusionKey({ source: "DRAFT" }, definition), "QA.SAFE_HOME");
});

test("opening more previews preserves the original Action return target", () => {
  const origin = preservePreviewOrigin({ id: "draft-main", source: "DRAFT" }, null);
  const preserved = preservePreviewOrigin({ id: "RELEASE:COMPOSITE@1.0.0", source: "RELEASE_VIEW" }, origin);

  assert.deepEqual(origin, { draftId: "draft-main" });
  assert.equal(preserved, origin);
  assert.deepEqual(preservePreviewOrigin(null, null), { draftId: null });
});

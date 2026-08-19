const test = require("node:test");
const assert = require("node:assert/strict");

const {
  describeActionLifecycle,
  nextAvailablePatchVersion
} = require("../../main/resources/static/action-lifecycle.js");

test("action lifecycle exposes only operations that preserve published-version immutability", () => {
  const editable = describeActionLifecycle({ draft: { status: "DRAFT" } });
  const published = describeActionLifecycle({
    draft: { status: "PUBLISHED" },
    release: { status: "PUBLISHED" }
  });
  const deprecated = describeActionLifecycle({
    draft: { status: "PUBLISHED" },
    release: { status: "DEPRECATED" }
  });

  assert.deepEqual(editable, {
    state: "DRAFT",
    statusLabel: "可编辑草稿",
    canEdit: true,
    canDeleteDraft: true,
    canCreateVersion: false,
    canDeprecateRelease: false
  });
  assert.equal(published.state, "PUBLISHED");
  assert.equal(published.canEdit, false);
  assert.equal(published.canCreateVersion, true);
  assert.equal(published.canDeprecateRelease, true);
  assert.equal(deprecated.state, "DEPRECATED");
  assert.equal(deprecated.canCreateVersion, true);
  assert.equal(deprecated.canDeprecateRelease, false);
});

test("new-version suggestion skips versions already used by either drafts or releases", () => {
  const suggestion = nextAvailablePatchVersion("1.2.3", ["1.2.4", "1.2.5", "2.0.0"]);

  assert.equal(suggestion, "1.2.6");
  assert.throws(() => nextAvailablePatchVersion("not-semver", []), /semantic version/i);
});

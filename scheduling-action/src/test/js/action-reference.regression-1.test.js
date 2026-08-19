const test = require("node:test");
const assert = require("node:assert/strict");

const { parseBindingValue } = require("../../main/resources/static/action-reference.js");

// Regression: ISSUE-001 — 空数值固定值被静默转换成 0
// Found by /qa on 2026-08-18
// Report: .gstack/qa-reports/qa-report-localhost-2026-08-18.md
test("empty numeric literals are rejected instead of silently becoming zero", () => {
  assert.throws(() => parseBindingValue("", { type: "NUMBER" }, "LITERAL"), /number/i);
  assert.throws(() => parseBindingValue("   ", { type: "INTEGER" }, "LITERAL"), /integer/i);
});

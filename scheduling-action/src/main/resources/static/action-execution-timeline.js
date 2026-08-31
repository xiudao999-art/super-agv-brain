(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionExecutionTimeline = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const labels = {
    STEP_STARTED: "开始执行", STEP_SUCCEEDED: "执行成功", STEP_FAILED: "执行失败",
    STEP_RETRY_PENDING: "等待重试", STEP_SKIPPED: "已跳过",
    STEP_VERIFICATION: "重试前复核", STEP_POLICY_APPLIED: "失败策略已生效",
    EVIDENCE_CAPTURED: "异常证据已采集"
  };

  function render(events, fallbackSteps, commandInput) {
    const progressEvents = Array.isArray(events) ? events.filter(item => item && item.stepEvent) : [];
    if (progressEvents.length) return progressEvents.map(item => renderProgress(item, commandInput)).join("");
    return renderFallback(fallbackSteps);
  }

  function renderProgress(item, commandInput) {
    const progress = item.stepEvent || {};
    const eventType = String(progress.eventType || "STEP_EVENT");
    const stepState = String(progress.stepState || item.state || "RUNNING");
    const stepId = progress.stepId || "未知步骤";
    const failed = eventType.includes("FAILED") || stepState.includes("FAILED");
    const success = eventType === "STEP_SUCCEEDED";
    const targetParams = findStepParams(commandInput, stepId);
    const attempt = progress.attempt == null ? "" : `<span>第 ${escapeHtml(progress.attempt)} 次</span>`;
    const duration = progress.durationMs == null ? "" : `<span>耗时 ${escapeHtml(progress.durationMs)} ms</span>`;
    return `<article class="timeline-event ${failed ? "failed" : success ? "success" : "running"}">`
      + `<div class="timeline-event-head"><b>${escapeHtml(progress.stepSequence || "·")}</b>`
      + `<div><strong>${escapeHtml(stepId)}</strong><small>${escapeHtml(progress.operation || "")}</small></div>`
      + `<em>${escapeHtml(labels[eventType] || eventType)}</em></div>`
      + `<div class="timeline-meta"><span>${escapeHtml(stepState)}</span>${attempt}${duration}`
      + `<span>${escapeHtml(formatTime(progress.occurredAt || item.timestamp))}</span></div>`
      + renderJsonDetails("本次下发参数", targetParams)
      + renderJsonDetails("设备执行证据", progress.evidence)
      + (progress.deviceFault ? `<div class="timeline-error"><b>设备异常</b><pre>${escapeHtml(pretty(progress.deviceFault))}</pre></div>` : "")
      + "</article>";
  }

  function renderFallback(steps) {
    if (!Array.isArray(steps)) return "";
    return steps.map((step, index) => {
      const stepState = String(step.state || "待执行");
      return `<div class="timeline-step ${stepState.includes("FAILED") ? "failed" : ""}"><b>${index + 1}</b>`
        + `<span>${escapeHtml(step.stepId || step.operation || "步骤")}</span><small>${escapeHtml(stepState)}</small></div>`;
    }).join("");
  }

  function findStepParams(commandInput, stepId) {
    const steps = commandInput && commandInput.executionPlan && commandInput.executionPlan.steps;
    if (!Array.isArray(steps)) return null;
    const step = steps.find(item => item && item.stepId === stepId);
    return step ? step.params || {} : null;
  }

  function renderJsonDetails(label, value) {
    return value == null ? "" : `<details class="timeline-evidence"><summary>${escapeHtml(label)}</summary><pre>${escapeHtml(pretty(value))}</pre></details>`;
  }
  function formatTime(value) { const date = new Date(value); return !value || Number.isNaN(date.getTime()) ? String(value || "") : date.toLocaleString("zh-CN", { hour12: false }); }
  function pretty(value) { return JSON.stringify(value, null, 2); }
  function escapeHtml(value) { return String(value == null ? "" : value).replace(/[&<>"']/g, char => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", "\"":"&quot;", "'":"&#39;" }[char])); }
  return { render, findStepParams };
});

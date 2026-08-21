(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionExecutionTimeline = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const labels = {
    PHASE_STARTED: "开始执行",
    PHASE_SUCCEEDED: "执行成功",
    PHASE_FAILED: "执行失败",
    PHASE_RETRY_PENDING: "等待重试",
    PHASE_SKIPPED: "已跳过",
    PHASE_VERIFICATION: "重试前复核",
    PHASE_POLICY_APPLIED: "异常策略已生效",
    EVIDENCE_CAPTURED: "异常证据已采集"
  };

  function render(events, fallbackSteps, commandInput) {
    const progressEvents = Array.isArray(events)
      ? events.filter(item => item && item.phaseEvent)
      : [];
    if (progressEvents.length) {
      return progressEvents.map(item => renderProgress(item, commandInput)).join("");
    }
    return renderFallback(fallbackSteps);
  }

  function renderProgress(item, commandInput) {
    const progress = item.phaseEvent || {};
    const eventType = String(progress.eventType || "PHASE_EVENT");
    const stepState = String(progress.stepState || item.state || "RUNNING");
    const phaseId = progress.phaseId || "未知步骤";
    const failed = eventType.includes("FAILED") || stepState.includes("FAILED")
      || stepState === "GATE_FAILED";
    const success = eventType === "PHASE_SUCCEEDED";
    const reportState = item.reportState || {};
    const targetParams = findPhaseParams(commandInput, phaseId);
    const evidence = progress.evidence;
    const deviceError = progress.deviceError;
    const attempt = progress.attempt == null ? ""
      : "<span>第 " + escapeHtml(progress.attempt) + " 次</span>";
    const duration = progress.durationMs == null ? ""
      : "<span>耗时 " + escapeHtml(progress.durationMs) + " ms</span>";
    const time = formatTime(progress.occurredAt || item.timestamp);
    const report = reportState.robotState
      ? "<span>机器人 " + escapeHtml(reportState.robotState) + "</span>" : "";

    return '<article class="timeline-event ' + (failed ? "failed" : success ? "success" : "running") + '">'
      + '<div class="timeline-event-head"><b>' + escapeHtml(progress.stepSequence || "·") + "</b>"
      + "<div><strong>" + escapeHtml(phaseId) + "</strong><small>"
      + escapeHtml(progress.subAction || "") + "</small></div><em>"
      + escapeHtml(labels[eventType] || eventType) + "</em></div>"
      + '<div class="timeline-meta"><span>' + escapeHtml(stepState) + "</span>"
      + attempt + duration + report + "<span>" + escapeHtml(time) + "</span></div>"
      + renderJsonDetails("本次下发参数", targetParams)
      + renderJsonDetails("设备执行证据", evidence)
      + (deviceError ? '<div class="timeline-error"><b>设备异常</b><pre>'
        + escapeHtml(pretty(deviceError)) + "</pre></div>" : "")
      + "</article>";
  }

  function renderFallback(steps) {
    if (!Array.isArray(steps)) return "";
    return steps.map((step, index) => {
      const stepState = String(step.state || "待执行");
      const failed = stepState.includes("FAILED") || stepState === "ERROR";
      return '<div class="timeline-step ' + (failed ? "failed" : "") + '"><b>'
        + (index + 1) + "</b><span>" + escapeHtml(step.phaseId || step.subAction || "步骤")
        + "</span><small>" + escapeHtml(stepState) + "</small></div>";
    }).join("");
  }

  function findPhaseParams(commandInput, phaseId) {
    const phases = commandInput && commandInput.MainAction && commandInput.MainAction.phases;
    if (!Array.isArray(phases)) return null;
    const phase = phases.find(item => item && item.phaseId === phaseId);
    return phase ? phase.params || {} : null;
  }

  function renderJsonDetails(label, value) {
    if (value == null) return "";
    return '<details class="timeline-evidence"><summary>' + escapeHtml(label)
      + "</summary><pre>" + escapeHtml(pretty(value)) + "</pre></details>";
  }

  function formatTime(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value)
      : date.toLocaleString("zh-CN", { hour12: false });
  }

  function pretty(value) {
    return JSON.stringify(value, null, 2);
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g,
      char => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", "\"":"&quot;", "'":"&#39;" }[char]));
  }

  return { render, findPhaseParams };
});

package com.kunling.scheduling.action.execution.domain;

/**
 * 下游事件应用结果。
 *
 * <p>把“是否保留执行证据”和“是否继续通知上层”分开表达，避免重复或乱序事件
 * 被误写入事件表，也避免 UNKNOWN_HOLD 后的迟到事实重新推进流程。</p>
 */
public enum ActionEventApplyResult {
    /** messageId 未重复，但 sequence 已处理；整条事件丢弃。 */
    DROPPED(false, false),
    /** 事件有效，只补充终态后的证据，不再通知流程。 */
    EVIDENCE_ONLY(true, false),
    /** 事件有效且属于当前活动执行，需要持久化并通知上层。 */
    APPLIED(true, true);

    private final boolean persistent;
    private final boolean reportable;

    ActionEventApplyResult(boolean persistent, boolean reportable) {
        this.persistent = persistent;
        this.reportable = reportable;
    }

    public boolean persistent() {
        return persistent;
    }

    public boolean reportable() {
        return reportable;
    }
}

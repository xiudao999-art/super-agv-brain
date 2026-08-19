package com.kunling.scheduling.action.definition.application;

public record ActionChange(String path, ChangeKind kind, String before, String after, ChangeRisk risk) {

    public enum ChangeKind { ADDED, REMOVED, MODIFIED }

    public enum ChangeRisk { NORMAL, MEDIUM, HIGH }
}

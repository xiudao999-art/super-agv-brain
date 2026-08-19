package com.kunling.scheduling.action.definition.application;

import java.util.List;

public record ActionReleaseDiff(String actionKey, String fromVersion, String toVersion, List<ActionChange> changes) {
}

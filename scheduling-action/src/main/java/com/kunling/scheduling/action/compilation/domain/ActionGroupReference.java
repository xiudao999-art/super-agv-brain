package com.kunling.scheduling.action.compilation.domain;

public record ActionGroupReference(
        String actionKey,
        String actionVersion,
        String referenceStepId,
        String displayName) {
}

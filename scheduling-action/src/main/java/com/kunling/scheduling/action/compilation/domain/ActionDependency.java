package com.kunling.scheduling.action.compilation.domain;

public record ActionDependency(String actionKey, String version, String planHash) {
}

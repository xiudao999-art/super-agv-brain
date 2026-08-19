package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 发布计划固定下游计算的能力契约 Hash，不依赖上游维护版本号。 */
@JsonIgnoreProperties("version")
public record CapabilityRequirement(String capabilityKey, String contractHash) {
}

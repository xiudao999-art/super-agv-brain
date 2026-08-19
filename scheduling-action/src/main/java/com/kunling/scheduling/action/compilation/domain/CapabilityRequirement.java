package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 发布计划固定下游计算的能力契约 Hash，不依赖上游维护版本号。 */
@JsonIgnoreProperties("version")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CapabilityRequirement {
    String capabilityKey;
    String contractHash;
    @ConstructorProperties({"capabilityKey", "contractHash"})
    public CapabilityRequirement(
            String capabilityKey,
            String contractHash
    ) {
        this.capabilityKey = capabilityKey;
        this.contractHash = contractHash;
    }

}

package com.kunling.scheduling.action.capability.application;

import com.kunling.scheduling.action.compilation.domain.CapabilityRequirement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在 Action 实例落库前校验发布计划固定的契约 Hash。
 * 上游目录只保留当前契约，因此旧发布计划失配时必须重新编译，不能带着未知兼容性继续驱动设备。
 */
@Component
public class CapabilityContractGuard {

    private final CapabilityCatalog capabilityCatalog;

    public CapabilityContractGuard(CapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    public void verify(List<CapabilityRequirement> requirements) {
        for (CapabilityRequirement requirement : requirements) {
            var current = capabilityCatalog.find(requirement.capabilityKey())
                    .orElseThrow(() -> new IllegalArgumentException("上游目录中已不存在原子能力 "
                            + requirement.capabilityKey()));
            if (requirement.contractHash() == null
                    || !requirement.contractHash().equalsIgnoreCase(current.contractHash())) {
                throw new IllegalArgumentException("原子能力契约已变化，请重新编译并发布 Action："
                        + requirement.capabilityKey());
            }
        }
    }
}

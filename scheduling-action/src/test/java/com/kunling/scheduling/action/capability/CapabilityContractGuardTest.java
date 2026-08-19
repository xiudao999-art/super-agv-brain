package com.kunling.scheduling.action.capability;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.application.CapabilityContractGuard;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.compilation.domain.CapabilityRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityContractGuardTest {

    @Test
    void rejectsAPublishedPlanWhenTheCurrentCapabilityContractHasChanged() {
        CapabilityManifest current = new CapabilityManifest("arm.move.linear", "new-contract",
                ImmutableCollections.mapOf(), ImmutableCollections.mapOf(), ImmutableCollections.listOf("arm"), CapabilitySideEffect.PHYSICAL,
                CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, true);
        CapabilityCatalog catalog = new CapabilityCatalog() {
            public List<CapabilityManifest> listAll() { return ImmutableCollections.listOf(current); }
            public Optional<CapabilityManifest> find(String capabilityKey) { return Optional.of(current); }
        };

        CapabilityContractGuard guard = new CapabilityContractGuard(catalog);

        assertThatThrownBy(() -> guard.verify(ImmutableCollections.listOf(
                new CapabilityRequirement("arm.move.linear", "published-contract"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arm.move.linear")
                .hasMessageContaining("重新编译并发布");
    }
}

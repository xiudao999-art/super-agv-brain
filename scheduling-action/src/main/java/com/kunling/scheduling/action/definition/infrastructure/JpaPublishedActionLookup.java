package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.application.PublishedAction;
import com.kunling.scheduling.action.definition.application.PublishedActionLookup;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaPublishedActionLookup implements PublishedActionLookup {

    private final ActionReleaseRepository repository;
    private final JsonCodec jsonCodec;

    public JpaPublishedActionLookup(ActionReleaseRepository repository, JsonCodec jsonCodec) {
        this.repository = repository;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public Optional<PublishedAction> findPublished(String actionKey, String version) {
        return repository.findByActionKeyAndActionVersionAndStatus(actionKey, version, ActionReleaseStatus.PUBLISHED)
                .map(entity -> new PublishedAction(
                        jsonCodec.read(entity.getDefinitionJson(), ActionDefinition.class),
                        jsonCodec.read(entity.getPlanJson(), ExecutionPlan.class),
                        entity.getPlanHash()));
    }
}

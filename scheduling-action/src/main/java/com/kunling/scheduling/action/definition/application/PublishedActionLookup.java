package com.kunling.scheduling.action.definition.application;

import java.util.Optional;

public interface PublishedActionLookup {

    Optional<PublishedAction> findPublished(String actionKey, String version);
}

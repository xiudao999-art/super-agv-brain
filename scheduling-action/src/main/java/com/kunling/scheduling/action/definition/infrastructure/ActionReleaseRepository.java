package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionReleaseRepository extends JpaRepository<ActionReleaseEntity, String> {

    Optional<ActionReleaseEntity> findByActionKeyAndActionVersion(String actionKey, String actionVersion);

    Optional<ActionReleaseEntity> findByActionKeyAndActionVersionAndStatus(
            String actionKey, String actionVersion, ActionReleaseStatus status);

    List<ActionReleaseEntity> findAllByOrderByPublishedAtDesc();

    List<ActionReleaseEntity> findByActionKeyOrderByPublishedAtDesc(String actionKey);
}

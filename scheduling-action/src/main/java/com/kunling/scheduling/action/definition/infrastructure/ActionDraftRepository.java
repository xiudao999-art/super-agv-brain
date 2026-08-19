package com.kunling.scheduling.action.definition.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActionDraftRepository extends JpaRepository<ActionDraftEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from ActionDraftEntity draft where draft.id = :id")
    Optional<ActionDraftEntity> findByIdForUpdate(@Param("id") String id);

    Optional<ActionDraftEntity> findByActionKeyAndActionVersion(String actionKey, String actionVersion);

    List<ActionDraftEntity> findAllByOrderByUpdatedAtDesc();
}

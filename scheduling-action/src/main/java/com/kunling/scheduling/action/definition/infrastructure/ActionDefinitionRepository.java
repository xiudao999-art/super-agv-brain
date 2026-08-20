package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionDefinitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ActionDefinitionRepository extends JpaRepository<ActionDefinitionEntity, String> {

    Optional<ActionDefinitionEntity> findByActionKey(String actionKey);

    List<ActionDefinitionEntity> findAllByOrderByActionKeyAsc();

    List<ActionDefinitionEntity> findByStatusOrderByActionKeyAsc(ActionDefinitionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from ActionDefinitionEntity action where action.actionKey = :actionKey")
    Optional<ActionDefinitionEntity> findByActionKeyForUpdate(@Param("actionKey") String actionKey);
}

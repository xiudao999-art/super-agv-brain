package com.kunling.scheduling.action.definition.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ActionDefinitionRepository extends JpaRepository<ActionDefinitionEntity, String> {

    List<ActionDefinitionEntity> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from ActionDefinitionEntity action where action.id = :id")
    Optional<ActionDefinitionEntity> findByIdForUpdate(@Param("id") String id);
}

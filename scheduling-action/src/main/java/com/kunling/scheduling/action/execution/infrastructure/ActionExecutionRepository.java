package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionExecutionRepository extends JpaRepository<ActionExecutionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ActionExecutionEntity execution where execution.actionInstanceId = :id")
    Optional<ActionExecutionEntity> findByIdForUpdate(@Param("id") String id);

    List<ActionExecutionEntity> findByStateIn(Collection<ActionExecutionState> states);
}

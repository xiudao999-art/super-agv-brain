package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActionExecutionRepository extends JpaRepository<ActionExecutionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ActionExecutionEntity execution where execution.actionInstanceId = :id")
    Optional<ActionExecutionEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ActionExecutionEntity execution "
            + "where execution.state in :states order by execution.actionInstanceId")
    List<ActionExecutionEntity> findByStateInForUpdate(@Param("states") Collection<ActionExecutionState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ActionExecutionEntity execution "
            + "where execution.robotId = :robotId and execution.state in :states "
            + "order by execution.actionInstanceId")
    List<ActionExecutionEntity> findByRobotIdAndStateInForUpdate(
            @Param("robotId") String robotId,
            @Param("states") Collection<ActionExecutionState> states);

    boolean existsByRobotIdAndStateIn(String robotId, Collection<ActionExecutionState> states);

    Optional<ActionExecutionEntity> findFirstByActionDefinitionIdAndStateInOrderByCreatedAtDesc(
            String actionDefinitionId, Collection<ActionExecutionState> states);
}

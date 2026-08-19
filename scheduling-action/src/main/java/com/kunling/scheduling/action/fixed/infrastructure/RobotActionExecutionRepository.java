package com.kunling.scheduling.action.fixed.infrastructure;

import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RobotActionExecutionRepository extends JpaRepository<RobotActionExecutionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from RobotActionExecutionEntity execution where execution.actionInstanceId = :id")
    Optional<RobotActionExecutionEntity> findByIdForUpdate(@Param("id") String id);

    List<RobotActionExecutionEntity> findByStateIn(Collection<RobotActionExecutionState> states);

    List<RobotActionExecutionEntity> findByRobotIdAndStateIn(
            String robotId, Collection<RobotActionExecutionState> states);

    List<RobotActionExecutionEntity> findByRobotIdAndState(
            String robotId, RobotActionExecutionState state);
}

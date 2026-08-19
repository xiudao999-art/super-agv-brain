package com.kunling.scheduling.action.execution.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionExecutionNodeRepository extends JpaRepository<ActionExecutionNodeEntity, String> {

    List<ActionExecutionNodeEntity> findByActionInstanceIdOrderByNodeOrdinalAsc(String actionInstanceId);

    Optional<ActionExecutionNodeEntity> findByActionInstanceIdAndNodeOrdinal(String actionInstanceId, int nodeOrdinal);
}

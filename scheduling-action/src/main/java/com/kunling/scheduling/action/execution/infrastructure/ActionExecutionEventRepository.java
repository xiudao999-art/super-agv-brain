package com.kunling.scheduling.action.execution.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ActionExecutionEventRepository extends JpaRepository<ActionExecutionEventEntity, String> {
    List<ActionExecutionEventEntity> findByActionInstanceIdOrderByReceivedAtAscEventSequenceAsc(
            String actionInstanceId, Pageable pageable);
}

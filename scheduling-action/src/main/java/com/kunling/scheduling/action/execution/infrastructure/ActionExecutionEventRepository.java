package com.kunling.scheduling.action.execution.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionExecutionEventRepository extends JpaRepository<ActionExecutionEventEntity, String> {
}

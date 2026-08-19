package com.kunling.scheduling.action.fixed.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotActionEventRepository extends JpaRepository<RobotActionEventEntity, String> {
}

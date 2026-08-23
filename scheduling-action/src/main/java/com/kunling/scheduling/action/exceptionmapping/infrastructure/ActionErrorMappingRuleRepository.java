package com.kunling.scheduling.action.exceptionmapping.infrastructure;

import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ActionErrorMappingRuleRepository
        extends JpaRepository<ActionErrorMappingRuleEntity, String> {

    List<ActionErrorMappingRuleEntity> findAllByOrderByProfileIdAscPriorityDescRuleIdAsc();

    List<ActionErrorMappingRuleEntity> findByStatusOrderByPriorityDescRuleIdAsc(
            ErrorMappingRuleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from ActionErrorMappingRuleEntity rule where rule.ruleId = :ruleId")
    Optional<ActionErrorMappingRuleEntity> findByRuleIdForUpdate(@Param("ruleId") String ruleId);
}

package com.kunling.scheduling.action.commissioning.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ActionParameterSetRepository extends JpaRepository<ActionParameterSetEntity, String> {

    List<ActionParameterSetEntity> findByActionKeyOrderByNameAsc(String actionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select parameterSet from ActionParameterSetEntity parameterSet where parameterSet.id = :id")
    Optional<ActionParameterSetEntity> findByIdForUpdate(@Param("id") String id);
}

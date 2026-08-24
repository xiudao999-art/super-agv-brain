package com.kunling.scheduling.agvflow.service;

import com.kunling.scheduling.common.exception.InvalidRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 隔离实验室配置与库位模块的依赖，只通过稳定主键确认库位是否存在。
 */
@Component
public class LabLocationReferenceChecker {

    private final JdbcTemplate jdbcTemplate;

    public LabLocationReferenceChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void requireExisting(Long locationId) {
        if (locationId != null && !exists(locationId)) {
            throw new InvalidRequestException("关联库位不存在: " + locationId);
        }
    }

    public boolean exists(Long locationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM location WHERE id = ?", Integer.class, locationId);
        return count != null && count > 0;
    }
}

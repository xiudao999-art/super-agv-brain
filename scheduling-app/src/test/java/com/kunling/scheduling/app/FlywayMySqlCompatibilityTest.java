package com.kunling.scheduling.app;

import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Flyway 运行时 classpath 中包含 MySQL 数据库支持。
 *
 * <p>该测试仅检查 JDBC URL 的数据库类型识别，不会建立真实数据库连接，
 * 因而可以稳定拦截遗漏 flyway-mysql 扩展依赖的问题。</p>
 */
class FlywayMySqlCompatibilityTest {

    @Test
    void shouldRecognizeMySqlJdbcUrl() {
        DatabaseType databaseType = DatabaseTypeRegister.getDatabaseTypeForUrl(
                "jdbc:mysql://127.0.0.1:3306/kunling_action"
        );

        assertEquals("MySQL", databaseType.getName());
    }
}

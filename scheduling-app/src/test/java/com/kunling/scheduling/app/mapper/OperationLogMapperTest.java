package com.kunling.scheduling.app.mapper;

import com.kunling.scheduling.app.domain.OperationLogQueryCriteria;
import com.kunling.scheduling.app.domain.OperationLogStatus;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogMapperTest {

    @Test
    void Mapper可以写入完整操作结果且不依赖用户字段() throws Exception {
        try (Fixture fixture = createFixture("insert")) {
            SystemOperationLog operationLog = entry(
                    "动作执行", "开始执行动作包", OperationType.EXECUTE,
                    "POST", "/api/action-executions", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 18, 0));

            int insertedCount = fixture.mapper.insert(operationLog);

            assertThat(insertedCount).isEqualTo(1);
            assertThat(operationLog.getId()).isPositive();
            Map<String, Object> row = fixture.jdbcTemplate.queryForMap(
                    "SELECT * FROM system_operation_log");
            assertThat(row)
                    .containsEntry("module", "动作执行")
                    .containsEntry("operation_type", "EXECUTE")
                    .containsEntry("status", "SUCCESS")
                    .containsEntry("duration_ms", 10L)
                    .doesNotContainKeys("operator_name", "user_id", "username", "request_ip");
        }
    }

    @Test
    void Mapper分页查询支持组合条件并按时间倒序返回() throws Exception {
        try (Fixture fixture = createFixture("query")) {
            fixture.mapper.insert(entry("动作执行", "开始执行动作包", OperationType.EXECUTE,
                    "POST", "/api/action-executions", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 18, 0)));
            fixture.mapper.insert(entry("工作流程", "人工终止流程", OperationType.UPDATE,
                    "POST", "/api/workflows/instances/9/terminate", OperationLogStatus.FAILURE,
                    "终止失败", LocalDateTime.of(2026, 8, 25, 17, 0)));
            fixture.mapper.insert(entry("动作配置", "启用 Action", OperationType.PUBLISH,
                    "POST", "/api/actions/ARM.PICK/activate", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 16, 0)));
            OperationLogQueryCriteria criteria = new OperationLogQueryCriteria(
                    1, 20, "动作执行", OperationType.EXECUTE, OperationLogStatus.SUCCESS,
                    "post", "action-executions",
                    LocalDateTime.of(2026, 8, 25, 17, 30),
                    LocalDateTime.of(2026, 8, 25, 18, 30));

            long total = fixture.mapper.count(criteria);
            List<SystemOperationLog> records = fixture.mapper.selectPage(
                    criteria, criteria.offset());

            assertThat(total).isEqualTo(1L);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getOperation()).isEqualTo("开始执行动作包");
            assertThat(records.get(0).getRequestMethod()).isEqualTo("POST");
        }
    }

    @Test
    void Mapper批量删除只删除指定日志并返回实际数量() throws Exception {
        try (Fixture fixture = createFixture("delete")) {
            fixture.mapper.insert(entry("动作执行", "执行一", OperationType.EXECUTE,
                    "POST", "/api/action-executions", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 18, 0)));
            fixture.mapper.insert(entry("动作执行", "执行二", OperationType.EXECUTE,
                    "POST", "/api/action-executions", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 18, 1)));
            fixture.mapper.insert(entry("工作流程", "保留日志", OperationType.UPDATE,
                    "POST", "/api/workflows/instances/1/suspend", OperationLogStatus.SUCCESS,
                    null, LocalDateTime.of(2026, 8, 25, 18, 2)));
            List<Long> ids = fixture.jdbcTemplate.queryForList(
                    "SELECT id FROM system_operation_log WHERE module = '动作执行'", Long.class);

            int deletedCount = fixture.mapper.deleteByIds(ids);

            assertThat(deletedCount).isEqualTo(2);
            assertThat(fixture.jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system_operation_log", Long.class)).isEqualTo(1L);
        }
    }

    private SystemOperationLog entry(String module, String operation, OperationType operationType,
                                     String requestMethod, String requestUri,
                                     OperationLogStatus status, String errorMessage,
                                     LocalDateTime operatedAt) {
        return new SystemOperationLog(module, operation, operationType,
                "TestController.operation", requestMethod, requestUri,
                "{\"path\":{}}", null, status, errorMessage, operatedAt, 10L);
    }

    private Fixture createFixture(String name) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:operation_log_" + name
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE system_operation_log ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, module VARCHAR(64) NOT NULL, "
                + "operation VARCHAR(128) NOT NULL, operation_type VARCHAR(16) NOT NULL, "
                + "handler_method VARCHAR(255) NOT NULL, request_method VARCHAR(16) NOT NULL, "
                + "request_uri VARCHAR(512) NOT NULL, request_params VARCHAR(4000), "
                + "response_body VARCHAR(4000), status VARCHAR(16) NOT NULL, "
                + "error_message VARCHAR(2000), operated_at TIMESTAMP(3) NOT NULL, "
                + "duration_ms BIGINT NOT NULL)");

        Configuration configuration = new Configuration(new Environment(
                "operation-log-test", new JdbcTransactionFactory(), dataSource));
        String mapperPath = "mapper/OperationLogMapper.xml";
        try (InputStream mapperXml = new ClassPathResource(mapperPath).getInputStream()) {
            new XMLMapperBuilder(mapperXml, configuration, mapperPath,
                    configuration.getSqlFragments()).parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSession sqlSession = factory.openSession(true);
        return new Fixture(jdbcTemplate, sqlSession,
                sqlSession.getMapper(OperationLogMapper.class));
    }

    private static final class Fixture implements AutoCloseable {
        private final JdbcTemplate jdbcTemplate;
        private final SqlSession sqlSession;
        private final OperationLogMapper mapper;

        private Fixture(JdbcTemplate jdbcTemplate,
                        SqlSession sqlSession,
                        OperationLogMapper mapper) {
            this.jdbcTemplate = jdbcTemplate;
            this.sqlSession = sqlSession;
            this.mapper = mapper;
        }

        @Override
        public void close() {
            sqlSession.close();
        }
    }
}

package com.kunling.scheduling.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.kunling.scheduling.app.domain.ActionParameterSchema;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ActionParameterField;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterDataType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterOwnerType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.SaveRequest;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationIssue;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationResult;
import com.kunling.scheduling.app.mapper.ActionParameterSchemaMapper;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionParameterSchemaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ActionParameterSchemaService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:action-parameter-" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table action_parameter_schema ("
                + "id varchar(36) not null primary key, "
                + "owner_type varchar(32) not null, "
                + "owner_key varchar(128) not null, "
                + "schema_json clob not null, "
                + "created_at timestamp not null, "
                + "updated_at timestamp not null, "
                + "unique (owner_type, owner_key))");
        service = new ActionParameterSchemaService(
                new JdbcBackedMapper(jdbcTemplate),
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 未配置时返回空Schema() {
        ActionParameterSchema schema = service.get(ParameterOwnerType.MAIN_ACTION, "ACTION-MOVE");

        assertThat(schema.getOwnerType()).isEqualTo(ParameterOwnerType.MAIN_ACTION);
        assertThat(schema.getOwnerKey()).isEqualTo("ACTION-MOVE");
        assertThat(schema.getFields()).isEmpty();
    }

    @Test
    void 主Action和子Action独立保存且完整覆盖() {
        ActionParameterField later = field("speed", "速度", ParameterDataType.DECIMAL,
                false, objectMapper.getNodeFactory().numberNode(0.6),
                new BigDecimal("0.1"), new BigDecimal("2"), Collections.emptyList(), 20);
        ActionParameterField earlier = field("pointName", "地图点", ParameterDataType.STRING,
                true, TextNode.valueOf("P01"), null, null, Collections.emptyList(), 10);

        ActionParameterSchema savedMain = service.save(ParameterOwnerType.MAIN_ACTION, "MOVE",
                new SaveRequest(Arrays.asList(later, earlier)));
        service.save(ParameterOwnerType.SUB_ACTION, "MOVE", new SaveRequest(Collections.emptyList()));

        assertThat(savedMain.getFields()).extracting(ActionParameterField::getKey)
                .containsExactly("pointName", "speed");
        assertThat(service.get(ParameterOwnerType.MAIN_ACTION, "MOVE").getFields()).hasSize(2);
        assertThat(service.get(ParameterOwnerType.SUB_ACTION, "MOVE").getFields()).isEmpty();

        service.save(ParameterOwnerType.MAIN_ACTION, "MOVE",
                new SaveRequest(Collections.singletonList(earlier)));
        assertThat(service.get(ParameterOwnerType.MAIN_ACTION, "MOVE").getFields())
                .extracting(ActionParameterField::getKey)
                .containsExactly("pointName");
    }

    @Test
    void 非法Schema禁止保存且缺失fields不会误清空() {
        ActionParameterField first = field("speed", "速度", ParameterDataType.DECIMAL,
                false, IntNode.valueOf(3), new BigDecimal("5"), new BigDecimal("2"),
                Collections.emptyList(), 10);
        ActionParameterField duplicate = field("speed", "模式", ParameterDataType.ENUM,
                false, TextNode.valueOf("UNKNOWN"), null, null,
                Arrays.asList("AUTO", "AUTO"), 10);

        assertThatThrownBy(() -> service.save(ParameterOwnerType.MAIN_ACTION, "MOVE",
                new SaveRequest(Arrays.asList(first, duplicate))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("参数 Schema 校验失败", "共");

        assertThatThrownBy(() -> service.save(ParameterOwnerType.MAIN_ACTION, "MOVE",
                new SaveRequest(null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("fields 必须显式提交");
        assertThat(service.get(ParameterOwnerType.MAIN_ACTION, "MOVE").getFields()).isEmpty();
    }

    @Test
    void 参数值校验一次返回必填类型范围枚举和未知字段问题() throws Exception {
        List<ActionParameterField> fields = Arrays.asList(
                field("pointName", "地图点", ParameterDataType.STRING,
                        true, null, null, null, Collections.emptyList(), 10),
                field("retryCount", "重试次数", ParameterDataType.INTEGER,
                        false, null, BigDecimal.ZERO, new BigDecimal("3"),
                        Collections.emptyList(), 20),
                field("speed", "速度", ParameterDataType.DECIMAL,
                        false, null, BigDecimal.ZERO, new BigDecimal("2"),
                        Collections.emptyList(), 30),
                field("mode", "模式", ParameterDataType.ENUM,
                        false, null, null, null, Arrays.asList("AUTO", "MANUAL"), 40)
        );
        service.save(ParameterOwnerType.SUB_ACTION, "MOVE_TO_MAP_POINT", new SaveRequest(fields));

        JsonNode values = objectMapper.readTree("{"
                + "\"retryCount\":5,"
                + "\"speed\":\"0.6\","
                + "\"mode\":\"OTHER\","
                + "\"extra\":true}");
        ValidationResult result = service.validate(
                ParameterOwnerType.SUB_ACTION, "MOVE_TO_MAP_POINT", values);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(ValidationIssue::getCode)
                .containsExactly("REQUIRED", "OUT_OF_RANGE", "TYPE_MISMATCH",
                        "INVALID_ENUM", "UNKNOWN_FIELD");
        assertThat(result.getIssues()).extracting(ValidationIssue::getPath)
                .containsExactly("$.pointName", "$.retryCount", "$.speed", "$.mode", "$.extra");
        assertThat(result.getIssues()).extracting(ValidationIssue::getMessage)
                .allMatch(message -> message != null && !message.trim().isEmpty());
    }

    @Test
    void 数组对象只校验JSON根类型且可选字段允许null() throws Exception {
        List<ActionParameterField> fields = Arrays.asList(
                field("items", "条目", ParameterDataType.ARRAY,
                        false, objectMapper.readTree("[]"), null, null,
                        Collections.emptyList(), 10),
                field("options", "选项", ParameterDataType.OBJECT,
                        false, objectMapper.readTree("{}"), null, null,
                        Collections.emptyList(), 20),
                field("remark", "备注", ParameterDataType.STRING,
                        false, null, null, null, Collections.emptyList(), 30)
        );
        service.save(ParameterOwnerType.MAIN_ACTION, "ACTION-PICK", new SaveRequest(fields));

        ValidationResult valid = service.validate(ParameterOwnerType.MAIN_ACTION, "ACTION-PICK",
                objectMapper.readTree("{\"items\":[1,{\"x\":true}],\"options\":{\"a\":1},\"remark\":null}"));
        ValidationResult invalid = service.validate(ParameterOwnerType.MAIN_ACTION, "ACTION-PICK",
                objectMapper.readTree("{\"items\":{},\"options\":[]}"));

        assertThat(valid.isValid()).isTrue();
        assertThat(invalid.getIssues()).extracting(ValidationIssue::getPath)
                .containsExactly("$.items", "$.options");
    }

    @Test
    void 增量脚本只创建当前Schema表() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/db/alter/20260831_01_add_action_parameter_schema.sql")),
                StandardCharsets.UTF_8).toLowerCase();

        assertThat(count(sql, "create table")).isEqualTo(1);
        assertThat(sql)
                .contains("action_parameter_schema", "schema_json",
                        "unique (owner_type, owner_key)")
                .doesNotContain("revision", "version", "publish");
    }

    private ActionParameterField field(String key,
                                       String label,
                                       ParameterDataType type,
                                       boolean required,
                                       JsonNode defaultValue,
                                       BigDecimal minimum,
                                       BigDecimal maximum,
                                       List<String> enumValues,
                                       int sort) {
        return new ActionParameterField(key, label, type, required, defaultValue,
                null, null, minimum, maximum, enumValues, sort);
    }

    private int count(String source, String fragment) {
        int total = 0;
        int from = 0;
        while ((from = source.indexOf(fragment, from)) >= 0) {
            total++;
            from += fragment.length();
        }
        return total;
    }

    /** 测试替身只模拟 Mapper 的 SQL 结果，Service 和 Repository 均使用真实实现。 */
    private static final class JdbcBackedMapper implements ActionParameterSchemaMapper {
        private final JdbcTemplate jdbcTemplate;

        private JdbcBackedMapper(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public String findSchemaJson(String ownerType, String ownerKey) {
            List<String> values = jdbcTemplate.query(
                    "select schema_json from action_parameter_schema "
                            + "where owner_type = ? and owner_key = ?",
                    (resultSet, rowNumber) -> resultSet.getString(1),
                    ownerType, ownerKey);
            return values.isEmpty() ? null : values.get(0);
        }

        @Override
        public int update(String ownerType,
                          String ownerKey,
                          String schemaJson,
                          Timestamp updatedAt) {
            return jdbcTemplate.update(
                    "update action_parameter_schema set schema_json = ?, updated_at = ? "
                            + "where owner_type = ? and owner_key = ?",
                    schemaJson, updatedAt, ownerType, ownerKey);
        }

        @Override
        public int insert(String id,
                          String ownerType,
                          String ownerKey,
                          String schemaJson,
                          Timestamp createdAt,
                          Timestamp updatedAt) {
            return jdbcTemplate.update(
                    "insert into action_parameter_schema "
                            + "(id, owner_type, owner_key, schema_json, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?)",
                    id, ownerType, ownerKey, schemaJson, createdAt, updatedAt);
        }
    }
}

package com.kunling.scheduling.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogSchemaTest {

    @Test
    void 建表脚本仅创建一张日志表且不包含用户身份字段() throws Exception {
        String schema = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/db/create/system_operation_log_schema.sql")), StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(countOccurrences(schema, "create table")).isEqualTo(1);
        assertThat(schema)
                .contains("system_operation_log", "request_params", "response_body", "duration_ms")
                .doesNotContain("operator_name", "user_id", "username", "dept_name",
                        "request_ip", "client_ip");
    }

    private int countOccurrences(String source, String fragment) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(fragment, from)) >= 0) {
            count++;
            from += fragment.length();
        }
        return count;
    }
}

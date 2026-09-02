package com.kunling.scheduling.action.database;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

/** 约束已评审异常映射种子与当前精确匹配能力保持一致。 */
class ActionErrorMappingSeedDataContractTest {
    private static final String REVIEWED_MAPPING_SEED =
            "/db/data/20260901_01_seed_reviewed_action_error_mappings.sql";

    @Test
    void seedContainsOnlyReviewedExactRulesSupportedByCurrentRuntime() {
        String sql = readResource(REVIEWED_MAPPING_SEED);

        assertThat(sql)
                .contains("'HIK-CHASSIS-001'")
                .contains("'HIK-CHASSIS-002'")
                .contains("'HUAYAN-ARM-001'")
                .contains("'NAV_TIMEOUT'")
                .contains("'SOCKET_CLOSED'")
                .contains("'10006'")
                .doesNotContain("'HUAYAN-ARM-100'")
                .doesNotContain("'GRIPPER-001'")
                .doesNotContain("'SCCAMERA-001'")
                .doesNotContain("'SCCAMERA-002'")
                .doesNotContain("'GLOBAL-FALLBACK'");
    }

    @Test
    void seedGuardsIdempotencyAndAmbiguousActiveMatches() {
        String sql = readResource(REVIEWED_MAPPING_SEED);

        assertThat(sql)
                .contains("where not exists")
                .contains("json_valid(rule_json)")
                .contains("json_contains(current_rule.rule_json, seed.rule_json)")
                .contains("binary current_rule.profile_id <> binary seed.profile_id")
                .contains("binary current_rule.status = binary 'ACTIVE'")
                .contains("binary current_rule.rule_id = binary seed.rule_id")
                .contains("相同核心匹配键的 ACTIVE 规则")
                .contains("rollback")
                .contains("resignal");
    }

    private String readResource(String path) {
        InputStream input = ActionErrorMappingSeedDataContractTest.class.getResourceAsStream(path);
        assertThat(input).as(path).isNotNull();
        try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

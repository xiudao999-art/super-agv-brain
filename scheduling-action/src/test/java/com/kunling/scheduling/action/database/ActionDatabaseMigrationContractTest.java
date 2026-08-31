package com.kunling.scheduling.action.database;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDatabaseMigrationContractTest {
    private static final String MIGRATION =
            "/db/alter/20260831_01_simplify_action_composition.sql";
    private static final String PARTIAL_REPAIR =
            "/db/alter/20260831_02_repair_partial_action_composition.sql";

    @Test
    void migrationRebuildsLegacyTablesIntoCurrentActionSchema() {
        String sql = readResource(MIGRATION);

        assertThat(sql)
                .contains("assert_action_composition_cutover_ready")
                .contains("active_execution_count")
                .contains("drop table action_execution_event;")
                .contains("drop table action_execution;")
                .contains("drop table action_parameter_set;")
                .contains("drop table action_definition;")
                .contains("action_definition_id varchar(36) not null")
                .contains("protocol_version varchar(16) not null")
                .contains("physical_outcome varchar(32) not null")
                .contains("command_input_json longtext not null")
                .contains("fk_action_event_execution")
                .contains("ix_action_error_mapping_active")
                .doesNotContain("drop table action_error_mapping_rule");

        assertThat(sql.indexOf("drop table action_execution_event;"))
                .isLessThan(sql.indexOf("drop table action_execution;"));
        assertThat(sql.indexOf("drop table action_parameter_set;"))
                .isLessThan(sql.indexOf("drop table action_definition;"));
    }

    @Test
    void partialRepairRequiresEmptyHalfMigratedTablesAndLeavesOtherModulesUntouched() {
        String sql = readResource(PARTIAL_REPAIR);

        assertThat(sql)
                .contains("assert_action_partial_repair_ready")
                .contains("disposable_row_count")
                .contains("downstream_action_type")
                .contains("drop table action_execution_event;")
                .contains("drop table action_execution;")
                .contains("drop table action_definition;")
                .contains("action_definition_id varchar(36) not null")
                .contains("ix_action_error_mapping_active")
                .doesNotContain("drop table action_error_mapping_rule")
                .doesNotContain("drop table flow_action")
                .doesNotContain("drop table robot_action_execution");
    }

    private String readResource(String path) {
        InputStream input = ActionDatabaseMigrationContractTest.class.getResourceAsStream(path);
        assertThat(input).as(path).isNotNull();
        try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

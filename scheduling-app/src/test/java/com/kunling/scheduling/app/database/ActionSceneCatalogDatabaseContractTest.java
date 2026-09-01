package com.kunling.scheduling.app.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSceneCatalogDatabaseContractTest {
    private static final Path BASELINE = Paths.get(
            "src/main/resources/db/create/action_scene_catalog_schema.sql");
    private static final Path MIGRATION = Paths.get(
            "src/main/resources/db/alter/20260901_01_add_action_scene_catalog.sql");
    private static final Path INITIAL_DATA = Paths.get(
            "src/main/resources/db/data/action_scene_catalog_initial_data.sql");

    @Test
    void 空库基线与增量脚本使用相同表结构() throws IOException {
        String baseline = read(BASELINE);
        String migration = read(MIGRATION);

        assertEquals(normalizedCreateTable(baseline), normalizedCreateTable(migration));
        assertTrue(baseline.contains("constraint uk_action_scene_catalog_item unique "
                + "(item_type, scene_code, item_code)"));
        assertTrue(baseline.contains("constraint ck_action_scene_catalog_item_type check"));
        assertTrue(baseline.contains("constraint ck_action_scene_catalog_scene_key check"));
    }

    @Test
    void 初始数据包含五个场景和十三条原子操作() throws IOException {
        String seed = read(INITIAL_DATA);

        assertEquals(5, occurrences(seed, "select 'SCENE'"));
        assertEquals(13, occurrences(seed, "select 'OPERATION'"));
        assertTrue(seed.contains("'HOME' as scene_code, 'HOME' as item_code, '回零'"));
        assertTrue(seed.contains("'PICK', 'VISION.VERIFY_MATERIAL', '视觉物料确认'"));
        assertTrue(seed.contains("'CAPTURE', 'VISION.CAPTURE', '视觉拍照'"));
    }

    @Test
    void 重复执行初始数据脚本不会覆盖人工配置() throws IOException {
        String seed = read(INITIAL_DATA).toLowerCase(Locale.ROOT);

        assertTrue(seed.contains("where not exists"));
        assertTrue(seed.contains("existing.item_type = seed.item_type"));
        assertTrue(seed.contains("existing.scene_code = seed.scene_code"));
        assertTrue(seed.contains("existing.item_code = seed.item_code"));
        assertFalse(seed.contains("on duplicate key update"));
        assertFalse(seed.contains("update action_scene_catalog_item"));
    }

    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String normalizedCreateTable(String sql) {
        Matcher matcher = Pattern.compile(
                "(?is)create\\s+table\\s+action_scene_catalog_item\\s*\\(.*?\\)\\s*"
                        + "engine\\s*=\\s*innodb.*?;")
                .matcher(sql);
        assertTrue(matcher.find(), "缺少 action_scene_catalog_item 建表语句");
        return matcher.group().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private int occurrences(String source, String expected) {
        int count = 0;
        int position = 0;
        while ((position = source.indexOf(expected, position)) >= 0) {
            count++;
            position += expected.length();
        }
        return count;
    }
}

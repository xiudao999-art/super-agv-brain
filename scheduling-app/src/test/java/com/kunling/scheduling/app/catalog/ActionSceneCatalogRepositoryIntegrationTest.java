package com.kunling.scheduling.app.catalog;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionSceneCatalogRepositoryIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private ActionSceneCatalogRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:scene_catalog_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table action_scene_catalog_item ("
                + "id bigint auto_increment primary key, item_type varchar(16) not null, "
                + "scene_code varchar(32) not null, item_code varchar(128) not null, "
                + "display_name varchar(128) not null, sort_order integer not null, "
                + "enabled boolean not null)");

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        SqlSessionFactory sqlSessionFactory = Objects.requireNonNull(factoryBean.getObject());
        sqlSessionFactory.getConfiguration().addMapper(ActionSceneCatalogRepository.class);
        repository = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(ActionSceneCatalogRepository.class);
    }

    @Test
    void 业务场景过滤停用项并按顺序和编码稳定排序() {
        insert("SCENE", "PLACE", "PLACE", "放置", 30, true);
        insert("SCENE", "PICK", "PICK", "抓取", 10, true);
        insert("SCENE", "HOME", "HOME", "回零", 10, true);
        insert("SCENE", "MOVE", "MOVE", "移动", 0, false);

        List<String> codes = repository.selectEnabledBusinessScenes().stream()
                .map(ActionSceneCatalogItem::getItemCode)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("HOME", "PICK", "PLACE"), codes);
    }

    @Test
    void 原子操作过滤停用项并按配置顺序返回() {
        insert("SCENE", "PICK", "PICK", "抓取", 10, true);
        insert("OPERATION", "PICK", "MOVE_TO_POSE", "移动到位姿", 30, true);
        insert("OPERATION", "PICK", "GRIP.OPEN", "夹爪打开", 10, true);
        insert("OPERATION", "PICK", "GRIP.CLOSE", "夹爪闭合", 0, false);

        List<String> codes = repository.selectEnabledOperations("PICK").stream()
                .map(ActionSceneCatalogItem::getItemCode)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("GRIP.OPEN", "MOVE_TO_POSE"), codes);
        assertEquals(1, repository.countEnabledBusinessScene("PICK"));
    }

    private void insert(String itemType,
                        String sceneCode,
                        String itemCode,
                        String displayName,
                        int sortOrder,
                        boolean enabled) {
        jdbcTemplate.update("insert into action_scene_catalog_item "
                        + "(item_type, scene_code, item_code, display_name, sort_order, enabled) "
                        + "values (?, ?, ?, ?, ?, ?)",
                itemType, sceneCode, itemCode, displayName, sortOrder, enabled);
    }
}

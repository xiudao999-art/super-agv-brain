package com.kunling.scheduling.app.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSceneCatalogRepositoryContractTest {

    @Test
    void 仓储仅暴露三个只读查询() {
        Method[] methods = ActionSceneCatalogRepository.class.getDeclaredMethods();

        assertEquals(3, methods.length);
        assertTrue(Arrays.stream(methods).allMatch(method -> method.isAnnotationPresent(Select.class)));
    }

    @Test
    void 场景和操作查询均过滤启用项并稳定排序() throws Exception {
        String sceneSql = sqlOf("selectEnabledBusinessScenes");
        String operationSql = sqlOf("selectEnabledOperations", String.class);

        assertTrue(sceneSql.contains("item_type = 'scene'"));
        assertTrue(sceneSql.contains("enabled = true"));
        assertTrue(sceneSql.contains("order by sort_order asc, item_code asc"));
        assertTrue(operationSql.contains("item_type = 'operation'"));
        assertTrue(operationSql.contains("scene_code = #{scenecode}"));
        assertTrue(operationSql.contains("enabled = true"));
        assertTrue(operationSql.contains("order by sort_order asc, item_code asc"));
    }

    private String sqlOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Select select = ActionSceneCatalogRepository.class
                .getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(Select.class);
        return Arrays.stream(select.value())
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}

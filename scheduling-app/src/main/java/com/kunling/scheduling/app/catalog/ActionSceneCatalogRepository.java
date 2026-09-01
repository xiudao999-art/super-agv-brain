package com.kunling.scheduling.app.catalog;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** action_scene_catalog_item 表的只读查询仓储，不暴露任何写操作。 */
@Mapper
public interface ActionSceneCatalogRepository {

    @Select("select item_type, scene_code, item_code, display_name, sort_order, enabled "
            + "from action_scene_catalog_item "
            + "where item_type = 'SCENE' and enabled = true "
            + "order by sort_order asc, item_code asc")
    @ConstructorArgs({
            @Arg(column = "item_type", javaType = String.class),
            @Arg(column = "scene_code", javaType = String.class),
            @Arg(column = "item_code", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "sort_order", javaType = int.class),
            @Arg(column = "enabled", javaType = boolean.class)
    })
    List<ActionSceneCatalogItem> selectEnabledBusinessScenes();

    @Select("select count(*) "
            + "from action_scene_catalog_item "
            + "where item_type = 'SCENE' and scene_code = #{sceneCode} "
            + "and item_code = scene_code and enabled = true")
    int countEnabledBusinessScene(@Param("sceneCode") String sceneCode);

    @Select("select item_type, scene_code, item_code, display_name, sort_order, enabled "
            + "from action_scene_catalog_item "
            + "where item_type = 'OPERATION' and scene_code = #{sceneCode} and enabled = true "
            + "order by sort_order asc, item_code asc")
    @ConstructorArgs({
            @Arg(column = "item_type", javaType = String.class),
            @Arg(column = "scene_code", javaType = String.class),
            @Arg(column = "item_code", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "sort_order", javaType = int.class),
            @Arg(column = "enabled", javaType = boolean.class)
    })
    List<ActionSceneCatalogItem> selectEnabledOperations(@Param("sceneCode") String sceneCode);
}

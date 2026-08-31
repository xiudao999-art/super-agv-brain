package com.kunling.scheduling.app.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;

/** action_parameter_schema 表的最小 MyBatis Mapper。 */
@Mapper
public interface ActionParameterSchemaMapper {

    @Select("select schema_json from action_parameter_schema "
            + "where owner_type = #{ownerType} and owner_key = #{ownerKey}")
    String findSchemaJson(@Param("ownerType") String ownerType,
                          @Param("ownerKey") String ownerKey);

    @Update("update action_parameter_schema "
            + "set schema_json = #{schemaJson}, updated_at = #{updatedAt} "
            + "where owner_type = #{ownerType} and owner_key = #{ownerKey}")
    int update(@Param("ownerType") String ownerType,
               @Param("ownerKey") String ownerKey,
               @Param("schemaJson") String schemaJson,
               @Param("updatedAt") Timestamp updatedAt);

    @Insert("insert into action_parameter_schema "
            + "(id, owner_type, owner_key, schema_json, created_at, updated_at) "
            + "values (#{id}, #{ownerType}, #{ownerKey}, #{schemaJson}, #{createdAt}, #{updatedAt})")
    int insert(@Param("id") String id,
               @Param("ownerType") String ownerType,
               @Param("ownerKey") String ownerKey,
               @Param("schemaJson") String schemaJson,
               @Param("createdAt") Timestamp createdAt,
               @Param("updatedAt") Timestamp updatedAt);
}

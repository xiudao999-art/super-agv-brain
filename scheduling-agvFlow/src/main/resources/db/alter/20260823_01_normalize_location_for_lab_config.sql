-- location 空间字段归一化：空间、地图和坐标改由已发布的实验室配置按 location_id 推导。
-- 本脚本仅执行一次。执行前必须备份 location，并确认下方查询结果符合预期。

select count(*) as location_row_count_before_alter from location;

alter table location
    drop column space_name,
    drop column map_name,
    drop column owner_name,
    drop column coordinate_type,
    drop column map_x,
    drop column map_y,
    drop column map_yaw,
    drop column nav_point_code,
    drop column operation_point;

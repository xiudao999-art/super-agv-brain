package com.kunling.scheduling.app.mapper;

import com.kunling.scheduling.app.domain.HomeAgvStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 首页机器人设备状态统计。 */
@Mapper
public interface HomeAgvStatisticsMapper {

    @Select("SELECT COUNT(*) AS totalCount, "
            + "COALESCE(SUM(connection_status = 1 AND running_status = 1), 0) AS runningCount, "
            + "COALESCE(SUM(connection_status = 1 AND running_status IN (0, 2)), 0) AS idleWaitingCount, "
            + "COALESCE(SUM(connection_status = 1 AND running_status = 3), 0) AS chargingCount, "
            + "COALESCE(SUM(connection_status IS NULL OR connection_status <> 1 "
            + "OR running_status IS NULL OR running_status NOT IN (0, 1, 2, 3)), 0) AS abnormalCount "
            + "FROM robot_info WHERE is_deleted = 0 AND enabled = 1")
    HomeAgvStatisticsRow selectStatistics();
}

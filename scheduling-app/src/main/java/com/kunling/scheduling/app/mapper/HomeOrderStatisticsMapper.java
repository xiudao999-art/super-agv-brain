package com.kunling.scheduling.app.mapper;

import com.kunling.scheduling.app.domain.HomeOrderStatisticsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HomeOrderStatisticsMapper {
    @Select("select " +
            "coalesce(sum(case when create_time >= current_date() and create_time < date_add(current_date(), interval 1 day) then 1 else 0 end), 0) as todayReceivedCount, " +
            "coalesce(sum(case when status = 'RUNNING' then 1 else 0 end), 0) as runningCount, " +
            "coalesce(sum(case when status = 'QUEUED' then 1 else 0 end), 0) as queuedCount, " +
            "coalesce(sum(case when status = 'SUCCEEDED' then 1 else 0 end), 0) as completedCount, " +
            "coalesce(sum(case when status in ('FAILED', 'WAITING') then 1 else 0 end), 0) as abnormalCount, " +
            "group_concat(distinct case when create_time >= current_date() " +
            "and create_time < date_add(current_date(), interval 1 day) then source end " +
            "order by source separator ' / ') as sources " +
            "from customer_order where is_deleted = 0")
    HomeOrderStatisticsRow selectStatistics();
}

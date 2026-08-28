package com.kunling.scheduling.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.resp.RobotAlarmRecordResp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RobotAlarmRecordMapper extends BaseMapper<RobotAlarmRecord> {

    @Select({
            "<script>",
            "SELECT r.alarm_no AS alarmNo,",
            "       r.alarm_description AS alarmDescription,",
            "       r.handling_level AS handlingLevel,",
            "       CONCAT_WS('/', NULLIF(n.robot_id, ''), NULLIF(n.node_name, '')) AS robotNode,",
            "       r.created_at AS occurredAt,",
            "       r.handling_status AS handlingStatus",
            "FROM robot_alarm_record r",
            "LEFT JOIN flow_node n ON n.id = r.node_id AND n.is_deleted = 0",
            "WHERE r.is_deleted = 0",
            "<if test='alarmNo != null and alarmNo != \"\"'>",
            "  AND r.alarm_no LIKE CONCAT('%', #{alarmNo}, '%')",
            "</if>",
            "<if test='alarmCategoryCode != null and alarmCategoryCode != \"\"'>",
            "  AND r.alarm_category_code = #{alarmCategoryCode}",
            "</if>",
            "<if test='handlingStatus != null'>",
            "  AND r.handling_status = #{handlingStatus}",
            "</if>",
            "<if test='nodeId != null'>",
            "  AND r.node_id = #{nodeId}",
            "</if>",
            "ORDER BY r.created_at DESC",
            "</script>"
    })
    IPage<RobotAlarmRecordResp> selectRespPage(
            Page<RobotAlarmRecordResp> page,
            @Param("alarmNo") String alarmNo,
            @Param("alarmCategoryCode") String alarmCategoryCode,
            @Param("handlingStatus") Integer handlingStatus,
            @Param("nodeId") Long nodeId);
}

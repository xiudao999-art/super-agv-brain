package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.resp.RobotAlarmRecordResp;

public interface RobotAlarmRecordService extends IService<RobotAlarmRecord> {

    IPage<RobotAlarmRecordResp> pageResp(long pageNum, long pageSize, String alarmNo,
                                        String alarmCategoryCode, Integer handlingStatus, Long nodeId);
}

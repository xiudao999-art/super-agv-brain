package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.mapper.RobotAlarmRecordMapper;
import com.kunling.scheduling.workflow.resp.RobotAlarmRecordResp;
import com.kunling.scheduling.workflow.service.RobotAlarmRecordService;
import org.springframework.stereotype.Service;

@Service
public class RobotAlarmRecordServiceImpl
        extends ServiceImpl<RobotAlarmRecordMapper, RobotAlarmRecord>
        implements RobotAlarmRecordService {

    @Override
    public IPage<RobotAlarmRecordResp> pageResp(long pageNum, long pageSize, String alarmNo,
                                               String alarmCategoryCode, Integer handlingStatus, Long nodeId) {
        return baseMapper.selectRespPage(new Page<>(pageNum, pageSize), trimToNull(alarmNo),
                trimToNull(alarmCategoryCode), handlingStatus, nodeId);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

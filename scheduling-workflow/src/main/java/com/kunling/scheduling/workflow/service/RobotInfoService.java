package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kunling.scheduling.workflow.entity.RobotInfo;
import com.kunling.scheduling.workflow.resp.RobotInfoResp;

public interface RobotInfoService extends IService<RobotInfo> {

    IPage<RobotInfoResp> pageResp(long pageNum, long pageSize);
}

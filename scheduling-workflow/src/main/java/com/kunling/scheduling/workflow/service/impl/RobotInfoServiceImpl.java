package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.workflow.entity.HardwareInfo;
import com.kunling.scheduling.workflow.entity.RobotInfo;
import com.kunling.scheduling.workflow.mapper.RobotInfoMapper;
import com.kunling.scheduling.workflow.resp.RobotInfoResp;
import com.kunling.scheduling.workflow.service.HardwareInfoService;
import com.kunling.scheduling.workflow.service.RobotInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RobotInfoServiceImpl extends ServiceImpl<RobotInfoMapper, RobotInfo>
        implements RobotInfoService {

    private final HardwareInfoService hardwareInfoService;

    public RobotInfoServiceImpl(HardwareInfoService hardwareInfoService) {
        this.hardwareInfoService = hardwareInfoService;
    }

    @Override
    public IPage<RobotInfoResp> pageResp(long pageNum, long pageSize) {
        IPage<RobotInfo> robotPage = page(new Page<>(pageNum, pageSize));
        if (robotPage.getRecords().isEmpty()) {
            return robotPage.convert(this::toResp);
        }

        List<Long> robotIds = robotPage.getRecords().stream()
                .map(RobotInfo::getId)
                .collect(Collectors.toList());
        Map<Long, List<HardwareInfo>> modulesByRobotId = hardwareInfoService.list(
                        Wrappers.<HardwareInfo>lambdaQuery()
                                .in(HardwareInfo::getParentId, robotIds)
                                .orderByAsc(HardwareInfo::getId))
                .stream()
                .collect(Collectors.groupingBy(HardwareInfo::getParentId));

        return robotPage.convert(robot -> toResp(robot,
                modulesByRobotId.getOrDefault(robot.getId(), Collections.emptyList())));
    }

    private RobotInfoResp toResp(RobotInfo robot) {
        return toResp(robot, Collections.emptyList());
    }

    private RobotInfoResp toResp(RobotInfo robot, List<HardwareInfo> modules) {
        RobotInfoResp response = new RobotInfoResp();
        BeanUtils.copyProperties(robot, response);
        response.setModules(modules);
        long normalCount = modules.stream()
                .filter(module -> Integer.valueOf(1).equals(module.getStatus()))
                .count();
        response.setModuleStatus(normalCount + "/" + (modules.size() - normalCount));
        return response;
    }
}

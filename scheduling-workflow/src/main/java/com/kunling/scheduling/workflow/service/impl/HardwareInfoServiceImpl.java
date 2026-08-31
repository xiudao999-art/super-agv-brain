package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.workflow.entity.HardwareInfo;
import com.kunling.scheduling.workflow.mapper.HardwareInfoMapper;
import com.kunling.scheduling.workflow.service.HardwareInfoService;
import org.springframework.stereotype.Service;

@Service
public class HardwareInfoServiceImpl extends ServiceImpl<HardwareInfoMapper, HardwareInfo>
        implements HardwareInfoService {
}

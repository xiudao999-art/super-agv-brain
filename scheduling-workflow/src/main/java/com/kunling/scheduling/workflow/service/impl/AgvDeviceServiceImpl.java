package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.workflow.entity.AgvDevice;
import com.kunling.scheduling.workflow.mapper.AgvDeviceMapper;
import com.kunling.scheduling.workflow.service.AgvDeviceService;
import org.springframework.stereotype.Service;

@Service
public class AgvDeviceServiceImpl extends ServiceImpl<AgvDeviceMapper, AgvDevice>
        implements AgvDeviceService {
}

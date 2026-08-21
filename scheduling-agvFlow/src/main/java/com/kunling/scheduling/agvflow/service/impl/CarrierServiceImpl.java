package com.kunling.scheduling.agvflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.entity.Carrier;
import com.kunling.scheduling.agvflow.mapper.CarrierMapper;
import com.kunling.scheduling.agvflow.service.CarrierService;
import org.springframework.stereotype.Service;
@Service
public class CarrierServiceImpl extends ServiceImpl<CarrierMapper, Carrier> implements CarrierService { }

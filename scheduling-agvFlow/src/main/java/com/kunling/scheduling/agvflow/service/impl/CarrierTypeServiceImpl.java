package com.kunling.scheduling.agvflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.entity.CarrierType;
import com.kunling.scheduling.agvflow.mapper.CarrierTypeMapper;
import com.kunling.scheduling.agvflow.service.CarrierTypeService;
import org.springframework.stereotype.Service;
@Service
public class CarrierTypeServiceImpl extends ServiceImpl<CarrierTypeMapper, CarrierType> implements CarrierTypeService { }

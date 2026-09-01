package com.kunling.scheduling.app.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.app.domain.entity.CarrierType;
import com.kunling.scheduling.app.mapper.CarrierTypeMapper;
import com.kunling.scheduling.app.service.CarrierTypeService;
import org.springframework.stereotype.Service;
@Service
public class CarrierTypeServiceImpl extends ServiceImpl<CarrierTypeMapper, CarrierType> implements CarrierTypeService { }

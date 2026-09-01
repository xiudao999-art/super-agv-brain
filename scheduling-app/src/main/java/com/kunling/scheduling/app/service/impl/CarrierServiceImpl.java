package com.kunling.scheduling.app.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.app.domain.entity.Carrier;
import com.kunling.scheduling.app.mapper.CarrierMapper;
import com.kunling.scheduling.app.service.CarrierService;
import org.springframework.stereotype.Service;
@Service
public class CarrierServiceImpl extends ServiceImpl<CarrierMapper, Carrier> implements CarrierService { }

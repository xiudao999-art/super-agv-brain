package com.kunling.scheduling.agvflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.entity.LocationType;
import com.kunling.scheduling.agvflow.mapper.LocationTypeMapper;
import com.kunling.scheduling.agvflow.service.LocationTypeService;
import org.springframework.stereotype.Service;
@Service
public class LocationTypeServiceImpl extends ServiceImpl<LocationTypeMapper, LocationType> implements LocationTypeService { }

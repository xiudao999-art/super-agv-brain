package com.kunling.scheduling.app.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.app.domain.entity.LocationType;
import com.kunling.scheduling.app.mapper.LocationTypeMapper;
import com.kunling.scheduling.app.service.LocationTypeService;
import org.springframework.stereotype.Service;
@Service
public class LocationTypeServiceImpl extends ServiceImpl<LocationTypeMapper, LocationType> implements LocationTypeService { }

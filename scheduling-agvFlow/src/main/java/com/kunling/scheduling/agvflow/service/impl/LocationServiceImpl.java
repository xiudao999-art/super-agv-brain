package com.kunling.scheduling.agvflow.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.entity.Location;
import com.kunling.scheduling.agvflow.mapper.LocationMapper;
import com.kunling.scheduling.agvflow.service.LocationService;
import org.springframework.stereotype.Service;
@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService { }

package com.kunling.scheduling.app.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.app.domain.entity.Location;
import com.kunling.scheduling.app.mapper.LocationMapper;
import com.kunling.scheduling.app.service.LocationService;
import org.springframework.stereotype.Service;
@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService { }

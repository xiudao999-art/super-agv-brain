package com.kunling.scheduling.workflow.order.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderTaskMapper extends BaseMapper<OrderTask> { }

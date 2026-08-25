package com.kunling.scheduling.workflow.order.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> { }

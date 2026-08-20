package com.kunling.scheduling.agvflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {
}

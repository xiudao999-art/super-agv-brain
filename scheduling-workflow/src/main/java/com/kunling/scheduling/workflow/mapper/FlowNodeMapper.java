package com.kunling.scheduling.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.kunling.scheduling.workflow.entity.FlowNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {
}

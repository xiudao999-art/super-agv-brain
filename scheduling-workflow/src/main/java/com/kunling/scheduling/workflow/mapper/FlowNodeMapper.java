package com.kunling.scheduling.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.kunling.scheduling.workflow.entity.FlowNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlowNodeMapper extends BaseMapper<FlowNode> {

    /**
     * 锁定 Action 执行对应的流程节点，保证重复终态报告只会推进一次。
     */
    @Select("select * from flow_node "
            + "where action_instance_id = #{actionInstanceId} and is_deleted = 0 "
            + "limit 1 for update")
    FlowNode selectByActionInstanceIdForUpdate(@Param("actionInstanceId") String actionInstanceId);
}

package com.kunling.scheduling.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.kunling.scheduling.workflow.entity.FlowTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlowTemplateMapper extends BaseMapper<FlowTemplate> {

    /** 查询引用指定BPMN模板、且订单正在进行或失败的流程数量。 */
    @Select("select count(distinct ft.id) from flow_template ft " +
            "inner join order_task ot on ot.flow_template_id = ft.id and ot.is_deleted = 0 " +
            "inner join customer_order co on co.id = ot.order_id and co.is_deleted = 0 " +
            "where ft.source_template_id = #{workflowTemplateId} and ft.is_deleted = 0 " +
            "and co.status in ('RUNNING', 'FAILED')")
    long countPublishBlockedFlows(@Param("workflowTemplateId") Long workflowTemplateId);
}

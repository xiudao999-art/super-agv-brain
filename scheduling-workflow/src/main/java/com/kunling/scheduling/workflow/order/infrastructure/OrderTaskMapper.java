package com.kunling.scheduling.workflow.order.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderTaskMapper extends BaseMapper<OrderTask> {

    @Select("select count(1) from order_task t " +
            "inner join customer_order o on o.id = t.order_id and o.is_deleted = 0 " +
            "where t.flow_template_id = #{flowTemplateId} and t.is_deleted = 0 " +
            "and o.status in ('RUNNING', 'FAILED')")
    long countEditingBlockedOrders(@Param("flowTemplateId") Long flowTemplateId);

    @Select({"<script>",
            "select order_id as orderId, count(*) as taskCount,",
            "sum(case when status = 'SUCCEEDED' then 1 else 0 end) as completedTaskCount",
            "from order_task where is_deleted = 0 and order_id in",
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "group by order_id",
            "</script>"})
    List<OrderTaskCount> countByOrderIds(@Param("orderIds") List<Long> orderIds);
}

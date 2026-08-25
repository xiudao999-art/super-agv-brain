package com.kunling.scheduling.app.mapper;

import com.kunling.scheduling.app.domain.OperationLogQueryCriteria;
import com.kunling.scheduling.app.domain.SystemOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** system_operation_log 表的 MyBatis 数据访问接口。 */
@Mapper
public interface OperationLogMapper {

    int insert(SystemOperationLog operationLog);

    long count(@Param("criteria") OperationLogQueryCriteria criteria);

    List<SystemOperationLog> selectPage(@Param("criteria") OperationLogQueryCriteria criteria,
                                        @Param("offset") long offset);

    int deleteByIds(@Param("ids") List<Long> ids);
}

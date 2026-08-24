package com.kunling.scheduling.agvflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LabConfigMapper extends BaseMapper<LabConfigEntity> {

    @Select("SELECT * FROM lab_config ORDER BY revision DESC FOR UPDATE")
    List<LabConfigEntity> selectAllForUpdate();
}

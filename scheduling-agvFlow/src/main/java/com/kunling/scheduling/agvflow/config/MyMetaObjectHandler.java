package com.kunling.scheduling.agvflow.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>配合 BaseEntity 上的 @TableField(fill = FieldFill.INSERT / INSERT_UPDATE) 使用：
 * <ul>
 *   <li>insertFill：插入时填充 createTime 和 updateTime</li>
 *   <li>updateFill：更新时填充 updateTime</li>
 * </ul>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        // 严格模式：仅在字段为空时才填充，避免覆盖业务显式赋值
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 严格模式：仅在字段为空时才填充
        this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
    }
}

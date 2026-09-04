package com.kunling.scheduling.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 公共插件配置。 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfiguration {

    /**
     * 注册分页拦截器，使 selectPage/page 方法执行分页 SQL 和总数 COUNT 查询。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 请求页超过最大页时不自动回到第一页，避免调用方误以为第一页就是目标页。
        pagination.setOverflow(false);
        // 单页上限，防止异常参数导致一次加载过多数据。
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}

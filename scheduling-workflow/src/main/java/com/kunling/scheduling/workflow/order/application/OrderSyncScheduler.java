package com.kunling.scheduling.workflow.order.application;

import com.kunling.scheduling.workflow.order.config.OrderSyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderSyncScheduler.class);
    private final OrderSyncService service;
    private final OrderSyncProperties properties;
    private final RedisTemplate<String,String> redisTemplate;

    public OrderSyncScheduler(OrderSyncService service, OrderSyncProperties properties, RedisTemplate<String,String> redisTemplate) {
        this.service = service;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

//    @Scheduled(fixedDelayString = "${kunling.workflow.order-sync.fixed-delay-ms:10000}")
    public void pullOrders() {
        log.info("开始执行客户订单同步");
        if (!properties.isEnabled()){
            log.info("客户订单同步已禁用，本次执行将被跳过");
            return;
        }
        String key = "order:sync:lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "locked");
        if (Boolean.FALSE.equals(locked)) {
            log.info("客户订单同步锁被占用，本次执行将被跳过");
            return;
        }
        try {
            OrderSyncResult result = service.syncAll();
            log.info("客户订单同步完成，pulled={}, created={}, updated={}",
                    result.getPulled(), result.getCreated(), result.getUpdated());
        } catch (RuntimeException exception) {
            log.error("客户订单同步失败，下次调度将从原水位重试", exception);
        }
    }
}

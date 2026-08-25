package com.kunling.scheduling.workflow.order.application;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class OrderSyncLockService {
    private static final String ORDER_LOCK_PREFIX = "workflow:order-sync:";
    private static final String ORDER_EXECUTION_LOCK_KEY = "workflow:order-execution";
    private static final DefaultRedisScript<Long> ACQUIRE_OR_RENEW = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 0 or redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); return 1 else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redisTemplate;

    public OrderSyncLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Key=workflow:order-sync:{source}，Value=上游订单号。 */
    public boolean tryOrderLock(String source, String upstreamOrderNo, Duration ttl) {
        return tryLock(orderLockKey(source), upstreamOrderNo, ttl);
    }

    public void unlockOrder(String source, String upstreamOrderNo) {
        unlock(orderLockKey(source), upstreamOrderNo);
    }

    /** 全局订单执行锁：Key固定，Value为订单ID；同一订单可续期并继续执行下一任务。 */
    public boolean tryOrderExecutionLock(Long orderId, Duration ttl) {
        if (orderId == null) throw new IllegalArgumentException("订单ID不能为空");
        Long acquired = redisTemplate.execute(ACQUIRE_OR_RENEW,
                Collections.singletonList(ORDER_EXECUTION_LOCK_KEY),
                String.valueOf(orderId), String.valueOf(ttl.toMillis()));
        return Long.valueOf(1L).equals(acquired);
    }

    public void unlockOrderExecution(Long orderId) {
        if (orderId != null) unlock(ORDER_EXECUTION_LOCK_KEY, String.valueOf(orderId));
    }

    public boolean tryLock(String key, String value, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    /** 只允许持有相同value的调用方释放锁，避免删除其他实例的新锁。 */
    public void unlock(String key, String value) {
        redisTemplate.execute(COMPARE_AND_DELETE, Collections.singletonList(key), value);
    }

    private String orderLockKey(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("订单来源不能为空");
        return ORDER_LOCK_PREFIX + source.trim().toUpperCase();
    }
}

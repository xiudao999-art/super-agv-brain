package com.kunling.scheduling.workflow.order.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.workflow.order.client.OrderPullClient;
import com.kunling.scheduling.workflow.order.client.PullOrderResponse;
import com.kunling.scheduling.workflow.order.client.PulledOrder;
import com.kunling.scheduling.workflow.order.config.OrderSyncProperties;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderSyncState;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderSyncStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class OrderSyncService {
    private final OrderPullClient pullClient;
    private final OrderSyncProperties properties;
    private final OrderPersistenceService persistenceService;
    private final OrderSyncStateMapper stateMapper;
    private final OrderSyncLockService lockService;
    private final CustomerOrderMapper customerOrderMapper;

    public OrderSyncService(OrderPullClient pullClient, OrderSyncProperties properties,
                            OrderPersistenceService persistenceService, OrderSyncStateMapper stateMapper,
                            OrderSyncLockService lockService, CustomerOrderMapper customerOrderMapper) {
        this.pullClient = pullClient;
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.stateMapper = stateMapper;
        this.lockService = lockService;
        this.customerOrderMapper = customerOrderMapper;
    }

    public OrderSyncResult syncAll() {
        int pulled = 0, created = 0, updated = 0;
        for (String source : properties.getSources()) {
            OrderSyncResult result = syncSource(source);
            pulled += result.getPulled();
            created += result.getCreated();
            updated += result.getUpdated();
        }
        return new OrderSyncResult(pulled, created, updated);
    }

    public OrderSyncResult syncSource(String source) {
        if (StringUtils.isBlank(source)){
            throw new IllegalArgumentException("订单来源不能为空");
        }
        OrderSyncState state = getOrCreateState(source.trim());
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = state.getLastSuccessAt() == null
                ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : state.getLastSuccessAt().minus(properties.getOverlap());
        state.setLastAttemptAt(to);
        state.setLastStatus("RUNNING");
        state.setErrorMessage(null);
        stateMapper.updateById(state);

        int pulled = 0, created = 0, updated = 0;
        try {
            int page = 1;
            boolean hasNext;
            do {
                PullOrderResponse response = pullClient.pull(source, from, to, page, properties.getPageSize());
                if (response == null) {
                    throw new IllegalStateException("客户订单接口返回null");
                }
                if (response.getOrders() != null) {
                    for (PulledOrder order : response.getOrders()) {
                        if (order == null || StringUtils.isBlank(order.getUpstreamOrderNo())) {
                            throw new IllegalArgumentException("客户接口返回的订单号不能为空");
                        }
                        String orderNo = order.getUpstreamOrderNo();
                        CustomerOrder customerOrder = customerOrderMapper.selectOne(Wrappers.<CustomerOrder>lambdaQuery()
                                .eq(CustomerOrder::getUpstreamOrderNo, orderNo)
                                .eq(CustomerOrder::getSource, source));
                        if (customerOrder != null){
                            log.info("订单 {} 已存在，跳过", orderNo);
                            continue;
                        }
                        OrderPersistResult result =  persistenceService.persist(source, order);
                        pulled++;
                        if (result.isCreated()) {
                            created++;
                        } else {
                            updated++;
                        }
                    }
                }
                hasNext = response.isHasNext();
                page++;
                if (page > 10000) throw new IllegalStateException("客户订单分页超过安全上限10000");
            } while (hasNext);

            state.setLastSuccessAt(to);
            state.setLastStatus("SUCCESS");
            state.setErrorMessage(null);
            stateMapper.updateById(state);
            return new OrderSyncResult(pulled, created, updated);
        } catch (RuntimeException exception) {
            state.setLastStatus("FAILED");
            state.setErrorMessage(limit(exception.getMessage(), 1024));
            stateMapper.updateById(state);
            throw exception;
        }
    }

    private OrderSyncState getOrCreateState(String source) {
        OrderSyncState state = stateMapper.selectOne(Wrappers.<OrderSyncState>lambdaQuery()
                .eq(OrderSyncState::getSource, source));
        if (state != null) {
            return state;
        }
        state = new OrderSyncState();
        state.setSource(source);
        state.setLastStatus("NEVER");
        state.setVersion(0);
        state.setCreateTime(LocalDateTime.now());
        state.setUpdateTime(state.getCreateTime());
        stateMapper.insert(state);
        return state;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}

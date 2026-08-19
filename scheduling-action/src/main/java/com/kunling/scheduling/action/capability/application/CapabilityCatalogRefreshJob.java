package com.kunling.scheduling.action.capability.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时刷新上游目录；刷新失败时保留最后一次有效快照，不影响配置控制台读取。 */
@Component
@ConditionalOnProperty(prefix = "kunling.action.upstream", name = "enabled", havingValue = "true")
public class CapabilityCatalogRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(CapabilityCatalogRefreshJob.class);
    private final CapabilityCatalogSyncService syncService;

    public CapabilityCatalogRefreshJob(CapabilityCatalogSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(initialDelayString = "1s",
            fixedDelayString = "${kunling.action.upstream.catalog-refresh-interval:5m}")
    public void refresh() {
        try {
            var result = syncService.synchronize();
            log.info("上游原子能力目录同步完成：收到 {}，新增 {}，契约更新 {}，未变化 {}",
                    result.received(), result.created(), result.updated(), result.unchanged());
        } catch (RuntimeException exception) {
            log.warn("上游原子能力目录同步失败，继续使用最后一次有效快照：{}", exception.getMessage());
        }
    }
}

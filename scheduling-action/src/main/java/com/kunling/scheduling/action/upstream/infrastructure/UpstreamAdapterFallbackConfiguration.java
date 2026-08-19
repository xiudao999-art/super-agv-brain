package com.kunling.scheduling.action.upstream.infrastructure;

import com.kunling.scheduling.action.upstream.application.AtomicActionGateway;
import com.kunling.scheduling.action.upstream.application.AtomicActionRequest;
import com.kunling.scheduling.action.upstream.application.AtomicActionResult;
import com.kunling.scheduling.action.upstream.application.UpstreamUnavailableException;
import com.kunling.scheduling.action.upstream.application.UpstreamCapabilitySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UpstreamAdapterFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(AtomicActionGateway.class)
    AtomicActionGateway unconfiguredAtomicActionGateway() {
        return new AtomicActionGateway() {
            @Override
            public AtomicActionResult execute(AtomicActionRequest request) {
                throw new UpstreamUnavailableException("尚未配置不可修改上游的原子 Action 调用 Adapter。");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(UpstreamCapabilitySource.class)
    UpstreamCapabilitySource unconfiguredCapabilitySource() {
        return () -> {
            throw new UpstreamUnavailableException("尚未配置不可修改上游的原子能力目录 Adapter。");
        };
    }
}

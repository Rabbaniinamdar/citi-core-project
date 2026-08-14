package com.citicore.user.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfiguration {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var address = exchange.getRequest().getRemoteAddress();

            String ip = (address != null && address.getAddress() != null)
                    ? address.getAddress().getHostAddress()
                    : "unknown";

            return Mono.just(ip);
        };
    }
}
package com.youkeda.gateway.filter;

import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;

@Component
public class RequestTimeFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestTimeFilter.class);

    private static final String START_TIME = "requestStarTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());
        return chain.filter(exchange).then(Mono.fromRunnable(()->{
            Long startTime = exchange.getAttribute(START_TIME);
            if (startTime != null) {
                log.info(exchange.getRequest().getURI().getRawPath() + " 请求耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            }

        }));
    }

        @Override
                public int getOrder() {
            return 0;
        }
    }





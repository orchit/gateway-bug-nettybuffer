package com.example.bugdemo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;


@Slf4j
@Configuration
public class RoutesConfiguration {

    @Value("${demo.target.apiservice:http://apiservice}")//fake
            String target;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        log.info("Target for {}: {}", "apiservice", target);
        return builder.routes()
                .route(p -> p
                        .path("/my-api")
                        .filters(f -> f
                                .stripPrefix(1)//get rid of /my-api in the request URI
                        )
                        .uri(target)
                )
                .build();
    }

    @Bean
    public GlobalFilter loggingIdFilter() {
        return (exchange, chain) -> {
            if (!exchange.getRequest().getHeaders().containsKey("X-CORRELATION-ID")) {
                exchange = exchange.mutate()
                        .request(exchange.getRequest().mutate()
                                .header("X-CORRELATION-ID", UUID.randomUUID().toString())
                                .build())
                        .build();
            }
            exchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-REQUEST-ID", UUID.randomUUID().toString())
                            .build())
                    .build();
            return chain.filter(exchange);
        };
    }
}

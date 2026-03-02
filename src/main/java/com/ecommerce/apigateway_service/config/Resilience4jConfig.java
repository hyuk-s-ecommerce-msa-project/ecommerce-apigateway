package com.ecommerce.apigateway_service.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> globalCustomConfiguration() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(1000)
                .minimumNumberOfCalls(10)
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build();

//        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
//                .maxConcurrentCalls(100)
//                .maxWaitDuration(Duration.ofMillis(500))
//                .build();

        return factory -> {
                factory.configureDefault(
                        id -> new Resilience4JConfigBuilder(id)
                                .timeLimiterConfig(timeLimiterConfig)
                                .circuitBreakerConfig(circuitBreakerConfig)
                                .build()
                );

            factory.addCircuitBreakerCustomizer(circuitBreaker -> {
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> System.out.println("### CircuitBreaker State Change: " + event.getStateTransition()))
                        .onError(event -> System.err.println("### CircuitBreaker Error: " + event.getThrowable().getMessage()))
                        .onIgnoredError(event -> System.out.println("### CircuitBreaker Ignored Error: " + event.getThrowable().getMessage()));
            }, "userLoginCircuitBreaker", "userServiceCircuitBreaker");
        };
    }
}

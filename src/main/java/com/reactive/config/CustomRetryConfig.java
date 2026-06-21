package com.reactive.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class CustomRetryConfig {

    @Bean
    public RetryConfig retryConfig(RetryProperties retryProperties){
        return RetryConfig
                .custom()
                .maxAttempts(retryProperties.getMaxAttempts())
                .waitDuration(retryProperties.getWaitDuration())
//                .retryExceptions(
//                        IOException.class,
//                        TimeoutException.class,
//                        WebClientRequestException.class,
//                        WebClientResponseException.class
//                )
                .ignoreExceptions(
                        IllegalArgumentException.class  // bad input, no point retrying
                )
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        Duration.ofMillis(500),   // initial interval
                        2.0,                      // multiplier: 500 → 1000 → 2000ms
                        0.3,                      // randomization factor ±30% jitter
                        Duration.ofSeconds(5)     // max interval cap
                ))
                .failAfterMaxAttempts(true)
                .build();
    }

    @Bean
    public RetryRegistry retryRegistry(RetryConfig retryConfig) {
        RetryRegistry registry = RetryRegistry.of(retryConfig);

        registry.retry("postsService").getEventPublisher()
                .onRetry(e -> log.warn(
                        "Retry attempt #{} for '{}' due to: {}",
                        e.getNumberOfRetryAttempts(),
                        e.getName(),
                        e.getLastThrowable().getMessage()
                ))
                .onError(e -> log.error(
                        "All {} attempts exhausted for '{}'",
                        e.getNumberOfRetryAttempts(),
                        e.getName()
                ));

        return registry;
    }
}

package com.reactive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties("app.retry")
@Configuration
@Data
public class RetryProperties {

    private Set<Integer> statusToRetry;
//    private int maxAttempts;
//    private Duration waitDuration;
//    private List<Integer> statusToRetry;
//
//    private long initialInterval;
//    private long intervalMultiplier;
//    private int jitter;
//    private long maxInterval;
}

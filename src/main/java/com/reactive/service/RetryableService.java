package com.reactive.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class RetryableService {

    @Retryable(maxRetries = 4, delay = 3000)
    public Mono<String> bar(int param) {
        log.info("executing bar");

        if (param > 1) {
            return Mono.error(new RuntimeException("Something went wrong"));
        }

        return Mono.just("test")
                .subscribeOn(Schedulers.boundedElastic());
    }


}

package com.reactive.config;

import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier(value = "posts")
    public WebClient webClient(RetryRegistry retryRegistry, RetryProperties retryProperties){
        Retry retry = retryRegistry.retry("postsService");


        ExchangeFilterFunction retryFilter = (request, next) ->
                next.exchange(request)
                        .flatMap(response -> {
                            if (retryProperties.getStatusToRetry().contains(response.statusCode().value())) {
                                return response.createError(); // turns it into an exception
                            }
                            return Mono.just(response);
                        })
                        .transformDeferred(RetryOperator.of(retry));

        return WebClient
                .builder()
                .baseUrl("https://jsonplaceholder.typicode.com/v2/")
                .defaultHeader("Content-Type", "Application/json")
                .filter(retryFilter)
                .build();
    }
}

package com.reactive.config;

import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("postsClient")
    public WebClient webClient(RetryRegistry retryRegistry, RetryProperties retryProperties) {
        Retry retry = retryRegistry.retry("postsRetry");
        RetryOperator<ClientResponse> retryOperator = RetryOperator.of(retry);
        Set<Integer> retryableStatuses = new HashSet<>(retryProperties.getStatusToRetry());
        ExchangeFilterFunction retryFilter = getFilterFunction(retryableStatuses, retryOperator);

        return WebClient
                .builder()
                .baseUrl("https://jsonplaceholder.typicode.com/v2/")
                .defaultHeader("Content-Type", "application/json")
                .filter(retryFilter)
                .build();
    }

    @Bean
    @Qualifier("anotherWebClient")
    public WebClient anotherWebClient() {
        return WebClient
                .builder()
                .baseUrl("https://jsonplaceholder.typicode.com/")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private static ExchangeFilterFunction getFilterFunction(Set<Integer> retryableStatuses, RetryOperator<ClientResponse> retryOperator) {
        Set<HttpMethod> mutationMethods = Set.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.POST);

        return (request, next) -> {
            if (mutationMethods.contains(request.method())) {
                return next.exchange(request);
            }

            return next.exchange(request)
                    .flatMap(response -> {
                        if (retryableStatuses.contains(response.statusCode().value())) {
                            return response.createError(); // turns it into an exception
                        }
                        return Mono.just(response);
                    })
                    .transformDeferred(retryOperator);
        };
    }
}

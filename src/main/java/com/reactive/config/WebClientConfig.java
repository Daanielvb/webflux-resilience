package com.reactive.config;

import com.reactive.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${posts.client.base-url}")
    private String baseUrl;

    @Bean
    @Qualifier("postsClient")
    public WebClient webClient(RetryRegistry retryRegistry, RetryProperties retryProperties, CircuitBreakerRegistry circuitBreakerRegistry) {
        Retry retry = retryRegistry.retry("postsRetry");
        retry.getEventPublisher()
                .onRetry(r -> log.info("Retrying event {}", r.getEventType()))
                .onError(r -> log.warn("Failed all retries attempts {}", r.getEventType()));
        RetryOperator<ClientResponse> retryOperator = RetryOperator.of(retry);
        Set<Integer> retryableStatuses = new HashSet<>(retryProperties.getStatusToRetry());

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("postsCb");
        CircuitBreakerOperator<ClientResponse> cbOperator = CircuitBreakerOperator.of(cb);

        ExchangeFilterFunction retryFilter = getFilterFunction(retryableStatuses, retryOperator, cbOperator);

        return WebClient
                .builder()
                .baseUrl(baseUrl)
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

    private static ExchangeFilterFunction getFilterFunction(Set<Integer> retryableStatuses,
                                                            RetryOperator<ClientResponse> retryOperator,
                                                            CircuitBreakerOperator<ClientResponse> cbOperator) {
        Set<HttpMethod> mutationMethods = Set.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.POST);

        return (request, next) -> {
            if (mutationMethods.contains(request.method())) {
                return next.exchange(request);
            }

            return next.exchange(request)
                    .flatMap(response -> {
                        if (retryableStatuses.contains(response.statusCode().value())) {
                            return response.createError();
                        }
                        return Mono.just(response);
                    })
                    .transformDeferred(retryOperator)
                    .transformDeferred(cbOperator)
                    .onErrorMap(CallNotPermittedException.class, ex ->
                            new ServiceUnavailableException(String.format("Service temporarily unavailable - circuit open. error=[%s]", ex.getLocalizedMessage()), ex))
                    .onErrorMap(WebClientResponseException.class, ex ->
                        new ServiceUnavailableException("Upstream error [%s] from [%s] %s"
                            .formatted(ex.getStatusCode(), request.method(), request.url()), ex));
        };
    }
}

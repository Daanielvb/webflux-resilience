package com.reactive;

import com.reactive.config.RetryProperties;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RetryTest {

    private MockWebServer mockWebServer;
    private WebClient webClient;
    private RetryProperties retryProperties;
    private RetryRegistry retryRegistry;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        retryProperties = new RetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setStatusToRetry(List.of(500, 502, 503, 504));

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(retryProperties.getMaxAttempts())
                .waitDuration(Duration.ofMillis(100)) // keep tests fast
                .retryExceptions(WebClientResponseException.class)
                .failAfterMaxAttempts(true)
                .build();

        retryRegistry = RetryRegistry.of(retryConfig);
        webClient = buildWebClient(retryRegistry, retryProperties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private WebClient buildWebClient(RetryRegistry retryRegistry, RetryProperties retryProperties) {
        Retry retry = retryRegistry.retry("postsService");

        ExchangeFilterFunction retryFilter = (request, next) ->
                next.exchange(request)
                        .flatMap(response -> {
                            if (retryProperties.getStatusToRetry().contains(response.statusCode().value())) {
                                return response.releaseBody()
                                        .then(Mono.error(new WebClientResponseException(
                                                response.statusCode().value(),
                                                response.statusCode().toString(),
                                                response.headers().asHttpHeaders(),
                                                null, null
                                        )));
                            }
                            return Mono.just(response);
                        })
                        .transformDeferred(RetryOperator.of(retry));

        return WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .filter(retryFilter)
                .build();
    }

    @Test
    void shouldRetry_untilSuccess() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        webClient.get().uri("/posts")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextMatches(r -> r.getStatusCode().is2xxSuccessful())
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    void shouldExhaustRetries_andThrowRetryExhaustedException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        webClient.get().uri("/posts")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .verifyErrorMatches(ex ->
                        ex instanceof MaxRetriesExceededException // resilience4j wraps it
                                || ex instanceof WebClientResponseException
                );

        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    void shouldNotRetry_forNonRetryableStatus() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        webClient.get().uri("/posts")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .verifyError(WebClientResponseException.BadRequest.class);

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void shouldTrackRetryEvents() {
        List<Integer> attemptNumbers = new ArrayList<>();

        retryRegistry.retry("postsService").getEventPublisher()
                .onRetry(e -> attemptNumbers.add(e.getNumberOfRetryAttempts()));

        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        webClient.get().uri("/posts")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

        assertThat(attemptNumbers).containsExactly(1, 2); // retried twice before success
    }

    @Test
    void shouldRespectBackoff_betweenAttempts() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        List<Long> timestamps = new ArrayList<>();

        retryRegistry.retry("postsService").getEventPublisher()
                .onRetry(e -> timestamps.add(System.currentTimeMillis()));

        webClient.get().uri("/posts")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

        assertThat(timestamps).hasSize(2);

        long gap1 = timestamps.get(1) - timestamps.get(0);
        assertThat(gap1).isGreaterThanOrEqualTo(100L);
    }
}

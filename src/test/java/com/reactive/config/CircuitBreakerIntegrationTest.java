package com.reactive.config;

import com.reactive.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CircuitBreakerIntegrationTest {

    static MockWebServer mockWebServer = new MockWebServer();

    @Autowired
    @Qualifier("postsClient")
    private WebClient webClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker cb;

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("posts.client.base-url", () -> mockWebServer.url("/").toString());
    }

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        // replace dispatcher to flush any responses left in the queue by previous tests
        mockWebServer.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new MockResponse().setResponseCode(200); // safe default
            }
        });

        cb = circuitBreakerRegistry.circuitBreaker("postsCb");
        cb.reset();
    }

    // restore queue-based dispatcher before each test that needs to control responses
    private void useQueueDispatcher() {
        mockWebServer.setDispatcher(new okhttp3.mockwebserver.QueueDispatcher());
    }

    // -- helpers --

    private void enqueue(int statusCode) {
        mockWebServer.enqueue(new MockResponse().setResponseCode(statusCode));
    }

    private String makeGetCall() {
        return webClient.get()
                .uri("/posts")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> reactor.core.publisher.Mono.just("ERROR:" + e.getClass().getSimpleName()))
                .block();
    }

    // -- tests --

    @Test
    @DisplayName("CB open throws ServiceUnavailableException and does not reach downstream")
    void shouldShortCircuitAndThrowServiceUnavailableWhenOpen() {
        cb.transitionToOpenState();
        int requestsBefore = mockWebServer.getRequestCount();

        StepVerifier.create(
                        webClient.get()
                                .uri("/posts")
                                .retrieve()
                                .bodyToMono(String.class)
                )
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ServiceUnavailableException.class);
                    assertThat(ex.getMessage()).contains("circuit open");
                })
                .verify();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(requestsBefore);
    }

    @Test
    @DisplayName("Mutation methods bypass CB — POST reaches downstream even when CB is open")
    void shouldBypassCBForMutationMethods() {
        useQueueDispatcher();
        cb.transitionToOpenState();

        enqueue(201);

        StepVerifier.create(
                        webClient.post()
                                .uri("/posts")
                                .retrieve()
                                .bodyToMono(Void.class)
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("CB transitions CLOSED -> OPEN -> HALF_OPEN -> CLOSED on recovery")
    void shouldRecoverThroughHalfOpen() {
        useQueueDispatcher();

        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        cb.transitionToHalfOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        for (int i = 0; i < 5; i++) {
            enqueue(200);
        }

        for (int i = 0; i < 5; i++) {
            StepVerifier.create(
                            webClient.get()
                                    .uri("/posts")
                                    .retrieve()
                                    .bodyToMono(String.class)
                    )
                    .verifyComplete();
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("CB stays OPEN if probe calls in HALF_OPEN fail")
    void shouldReturnToOpenIfHalfOpenProbesFail() {
        useQueueDispatcher();

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        for (int i = 0; i < 15; i++) {
            enqueue(503);
        }

        for (int i = 0; i < 5; i++) {
            makeGetCall();
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
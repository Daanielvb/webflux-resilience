package com.reactive.exception;

import com.reactive.controller.TestController;
import com.reactive.service.MyService;
import com.reactive.service.RetryableService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TestController.class)
@Import(CustomExceptionHandler.class)
class CustomExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MyService postsService;

    @MockitoBean
    private RetryableService retryableService;

    @Test
    @DisplayName("ServiceUnavailableException is mapped to 503 with ProblemDetail body")
    void shouldReturn503WhenServiceUnavailableExceptionThrown() {
        when(postsService.doSomething(any()))
                .thenReturn(Mono.error(new ServiceUnavailableException("Service temporarily unavailable - circuit open", null)));

        webTestClient.get()
                .uri("/ping?param=test")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.detail").isEqualTo("Service temporarily unavailable - circuit open");
    }

    @Test
    @DisplayName("Unhandled exception is mapped to 500 with generic ProblemDetail body")
    void shouldReturn500WhenUnhandledExceptionThrown() {
        when(postsService.doSomething(any()))
                .thenReturn(Mono.error(new RuntimeException("some internal detail that should not leak")));

        webTestClient.get()
                .uri("/ping?param=test")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.detail").isEqualTo("An unexpected error occurred");
    }
}

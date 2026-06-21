package com.reactive.controller;

import com.reactive.service.MyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@WebFluxTest(TestController.class)
class TestControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MyService myService;

    @Test
    void shouldReturnOk_whenServiceReturnsValue() {
        when(myService.doSomething("123")).thenReturn(Mono.just(Collections.emptyList()));

        webTestClient.get()
                .uri("/ping?param=123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class).isEqualTo(Collections.emptyList());
    }

    @Test
    void shouldReturn5xx_whenServiceThrows() {
        when(myService.doSomething("123"))
                .thenThrow(new RuntimeException("downstream failure"));

        webTestClient.get()
                .uri("/ping?param=123")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void shouldReturn4xx_whenParamIsMissing() {
        webTestClient.get()
                .uri("/ping") // no param
                .exchange()
                .expectStatus().is4xxClientError();
    }
}

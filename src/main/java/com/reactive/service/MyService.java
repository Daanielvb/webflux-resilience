package com.reactive.service;

import com.reactive.RetryableHttpException;
import com.reactive.controller.request.PostDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MyService {

    @Qualifier("postsClient")
    private final WebClient client;

    @Qualifier("anotherWebClient")
    private final WebClient anotherClient;

    public Mono<List<PostDTO>> doSomething(String param) {
        return Mono.just(param)
                .flatMap(it -> client.get().uri("/posts").retrieve().bodyToFlux(PostDTO.class).collectList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Retryable(maxRetries = 4, delay = 3000, includes = RetryableHttpException.class)
    public Mono<String> bar(int param) {
        log.info("executing bar, param={}", param);

        return anotherClient.get()
                .uri(uriBuilder -> uriBuilder.path("/posts2").queryParam("id", param).build())
                .retrieve()
                .onStatus(
                        status -> status.is5xxServerError() || status.value() == 429,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RetryableHttpException(response.statusCode().value())
                                ))
                )
                .bodyToMono(String.class);
    }

}

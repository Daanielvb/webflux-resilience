package com.reactive.service;

import com.reactive.controller.request.PostDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyService {

    @Qualifier("postsClient")
    private final WebClient client;


    public Mono<List<PostDTO>> doSomething(String param) {
        return Mono.just(param)
                .flatMap(it -> client.get().uri("/posts").retrieve().bodyToFlux(PostDTO.class).collectList())
                .subscribeOn(Schedulers.boundedElastic());
    }

}

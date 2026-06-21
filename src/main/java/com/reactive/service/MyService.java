package com.reactive.service;

import com.reactive.config.PostDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class MyService {

    private final WebClient client;

    public MyService(@Qualifier("posts") WebClient client){
        this.client = client;
    }

    public Mono<List<PostDTO>> doSomething(String param) {
        return Mono.just(param)
                .flatMap(it -> client.get().uri("/posts").retrieve().bodyToFlux(PostDTO.class).collectList())
                .subscribeOn(Schedulers.boundedElastic());
    }

}

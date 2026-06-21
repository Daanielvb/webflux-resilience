package com.reactive.reactive.controller;

import com.reactive.reactive.service.MyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/ping")
public class TestController {

    @Autowired
    private MyService service;

    @GetMapping
    public Mono<ResponseEntity<Object>> foo(@RequestParam(value = "param") String param){
        return Mono.just(service.doSomething(param))
                .map(ResponseEntity::ok);
    }
}

package com.reactive.controller;

import com.reactive.service.MyService;
import com.reactive.service.RetryableService;
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

    @Autowired
    private RetryableService retryableService;

    @GetMapping
    public Mono<ResponseEntity<Object>> foo(@RequestParam(value = "param") String param){
        return Mono.just(service.doSomething(param))
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/else")
    public Mono<ResponseEntity<Object>> doElse(@RequestParam(value = "param") Integer param){
        return service.bar(param)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/bar")
    public Mono<ResponseEntity<Object>> bar(@RequestParam(value = "param") Integer param){
        return retryableService.bar(param)
                .map(ResponseEntity::ok);
    }
}

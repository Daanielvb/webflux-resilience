package com.reactive.controller.request;

public record PostDTO(
        long userId,
        long id,
        String title,
        String body
){}

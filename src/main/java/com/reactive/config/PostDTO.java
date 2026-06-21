package com.reactive.config;

public record PostDTO(
        long userId,
        long id,
        String title,
        String body
){}

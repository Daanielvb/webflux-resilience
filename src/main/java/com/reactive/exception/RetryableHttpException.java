package com.reactive.exception;

import lombok.Getter;

@Getter
public class RetryableHttpException extends RuntimeException {
    private final int statusCode;

    public RetryableHttpException(int statusCode) {
        super("Retryable HTTP error: " + statusCode);
        this.statusCode = statusCode;
    }

}
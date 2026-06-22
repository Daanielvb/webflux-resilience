package com.reactive;

public class RetryableHttpException extends RuntimeException {
    private final int statusCode;

    public RetryableHttpException(int statusCode) {
        super("Retryable HTTP error: " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
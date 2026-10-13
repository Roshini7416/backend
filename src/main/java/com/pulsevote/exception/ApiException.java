package com.pulsevote.exception;

import org.springframework.http.HttpStatus;

// Our own error: carries an HTTP status (400, 404...) and a friendly message.
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.citicore.user.exception;

public class UnauthorizedAccountAccessException extends RuntimeException {

    public UnauthorizedAccountAccessException(String message) {
        super(message);
    }
}
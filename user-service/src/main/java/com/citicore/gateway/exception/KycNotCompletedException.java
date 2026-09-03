package com.citicore.gateway.exception;

public class KycNotCompletedException extends RuntimeException {

    public KycNotCompletedException(String message) {
        super(message);
    }
}
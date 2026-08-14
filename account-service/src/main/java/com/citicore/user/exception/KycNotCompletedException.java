package com.citicore.user.exception;

public class KycNotCompletedException extends RuntimeException {

    public KycNotCompletedException(String message) {
        super(message);
    }
}

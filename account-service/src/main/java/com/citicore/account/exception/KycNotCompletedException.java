package com.citicore.account.exception;

public class KycNotCompletedException extends RuntimeException {

    public KycNotCompletedException(String message) {
        super(message);
    }
}

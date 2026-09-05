package com.citicore.transaction.exception;

public class KycNotCompletedException extends RuntimeException {

    public KycNotCompletedException(String message) {
        super(message);
    }
}
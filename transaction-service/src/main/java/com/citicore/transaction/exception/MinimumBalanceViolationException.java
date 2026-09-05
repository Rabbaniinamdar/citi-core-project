package com.citicore.transaction.exception;

public class MinimumBalanceViolationException extends RuntimeException {

    public MinimumBalanceViolationException(String message) {
        super(message);
    }
}
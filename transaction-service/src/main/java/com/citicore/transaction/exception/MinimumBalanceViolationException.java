
package com.citicore.gateway.exception;

public class MinimumBalanceViolationException extends RuntimeException {

    public MinimumBalanceViolationException(String message) {
        super(message);
    }
}
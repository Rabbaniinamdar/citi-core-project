
package com.citicore.user.exception;

public class MinimumBalanceViolationException extends RuntimeException {

    public MinimumBalanceViolationException(String message) {
        super(message);
    }
}
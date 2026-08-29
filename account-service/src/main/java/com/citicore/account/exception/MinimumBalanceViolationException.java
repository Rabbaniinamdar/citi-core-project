
package com.citicore.account.exception;

public class MinimumBalanceViolationException extends RuntimeException {

    public MinimumBalanceViolationException(String message) {
        super(message);
    }
}
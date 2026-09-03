package com.citicore.gateway.exception;

public class KycAlreadyApprovedException extends RuntimeException{
    public KycAlreadyApprovedException(String message) {
        super(message);
    }

}

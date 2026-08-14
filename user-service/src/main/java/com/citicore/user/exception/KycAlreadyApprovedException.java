package com.citicore.user.exception;

public class KycAlreadyApprovedException extends RuntimeException{
    public KycAlreadyApprovedException(String message) {
        super(message);
    }

}

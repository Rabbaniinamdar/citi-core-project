package com.citicore.user.exception;

public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException(String message) {
        super(message);
    }
}
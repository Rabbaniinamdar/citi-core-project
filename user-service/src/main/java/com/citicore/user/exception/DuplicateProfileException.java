package com.citicore.user.exception;

public class DuplicateProfileException extends RuntimeException {

    public DuplicateProfileException(String message) {
        super(message);
    }
}
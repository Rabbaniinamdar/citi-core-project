package com.citicore.user.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
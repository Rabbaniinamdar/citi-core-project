package com.citicore.account.exception;

public class ReplicaUnavailableException extends RuntimeException {

    public ReplicaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
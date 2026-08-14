package com.citicor.auth.exception;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {
    private int status;
    private String message;
    private List<String> details;
    LocalDateTime now;
    public ErrorResponse(int status, String message, List<String> details, LocalDateTime now) {
        this.status = status;
        this.message = message;
        this.now=now;
        this.details = details;
    }
}
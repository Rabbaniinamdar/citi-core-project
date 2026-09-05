package com.citicore.transaction.exception;

import com.citicore.transaction.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

import javax.security.auth.login.AccountNotFoundException;
import java.util.*;

@ResponseBody
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================
    // VALIDATION ERRORS
    // =========================
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<String> errors = new ArrayList<>();

        ex.getConstraintViolations().forEach(v ->
                errors.add(v.getPropertyPath() + " : " + v.getMessage())
        );

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", errors.toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        List<String> errors = new ArrayList<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(error.getField() + " : " + error.getDefaultMessage());
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", errors.toString()));
    }

    // =========================
    // BUSINESS EXCEPTIONS
    // =========================
    @ExceptionHandler(DailyLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleDailyLimit(
            DailyLimitExceededException ex) {

        return buildError("DAILY_LIMIT_EXCEEDED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<?>> handleInsufficient(
            InsufficientBalanceException ex) {

        return buildError("INSUFFICIENT_BALANCE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MinimumBalanceViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleMinBalance(
            MinimumBalanceViolationException ex) {

        return buildError("MINIMUM_BALANCE_VIOLATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleAccountNotFound(
            AccountNotFoundException ex) {

        return buildError("ACCOUNT_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(KycNotCompletedException.class)
    public ResponseEntity<ApiResponse<?>> handleKyc(
            KycNotCompletedException ex) {

        return buildError("KYC_NOT_COMPLETED", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ApiResponse<?>> handleDuplicate(
            DuplicateAccountException ex) {

        return buildError("DUPLICATE_ACCOUNT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // =========================
    // GENERIC
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {

        return buildError("INTERNAL_SERVER_ERROR",
                "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<?>> buildError(
            String code, String message, HttpStatus status) {

        return new ResponseEntity<>(
                ApiResponse.failure(code, message),
                status
        );
    }
}
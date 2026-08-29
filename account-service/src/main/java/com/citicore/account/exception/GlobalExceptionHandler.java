
package com.citicore.account.exception;

import com.citicore.account.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class    GlobalExceptionHandler {

    // =========================
    // VALIDATION
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
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleAccountNotFound(
            AccountNotFoundException ex) {

        return build("ACCOUNT_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<?>> handleInsufficient(
            InsufficientBalanceException ex) {

        return build("INSUFFICIENT_BALANCE", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MinimumBalanceViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleMinBalance(
            MinimumBalanceViolationException ex) {

        return build("MINIMUM_BALANCE_VIOLATION", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ApiResponse<?>> handleDuplicate(
            DuplicateAccountException ex) {

        return build("DUPLICATE_ACCOUNT", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // =========================
    // GENERIC
    // =========================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {

        System.out.println("🔥 GENERIC EXCEPTION: " + ex.getMessage());

        return build("INTERNAL_SERVER_ERROR",
                "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<?>> build(
            String code, String message, HttpStatus status) {

        return ResponseEntity
                .status(status)
                .body(ApiResponse.failure(code, message));
    }
}
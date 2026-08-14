package com.citicor.auth.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("status", ex.getStatus().value());
        error.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(error, ex.getStatus());
    }

    // Validation Errors (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, Object> error = new HashMap<>();

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        error.put("message", "Validation failed");
        error.put("errors", errors);
        error.put("status", 400);
        error.put("timestamp", LocalDateTime.now());

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("message", "Something went wrong");
        error.put("status", 500);
        error.put("timestamp", LocalDateTime.now());

        return ResponseEntity.internalServerError().body(error);
    }
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<Map<String, Object>> handleSignatureException(SignatureException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("message", "Invalid JWT signature");
        error.put("status", HttpStatus.UNAUTHORIZED);
        error.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
//        return buildResponse("Invalid JWT signature", HttpStatus.UNAUTHORIZED);
    }

    // ✅ TOKEN EXPIRED
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwt(ExpiredJwtException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", "JWT token has expired");
        error.put("status", HttpStatus.UNAUTHORIZED);
        error.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
//        return buildResponse("JWT token has expired", HttpStatus.UNAUTHORIZED);
    }

    // ✅ MALFORMED TOKEN
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJwt(MalformedJwtException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", "Malformed JWT token");
        error.put("status", HttpStatus.UNAUTHORIZED);
        error.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
//        return buildResponse("Malformed JWT token", HttpStatus.UNAUTHORIZED);
    }

    // ✅ DESERIALIZATION ERROR (your current error)
    @ExceptionHandler(DeserializationException.class)
    public ResponseEntity<Map<String, Object>> handleDeserialization(DeserializationException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("message", "Invalid JWT format (corrupted token)");
        error.put("status", HttpStatus.UNAUTHORIZED);
        error.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
//        return buildResponse("Invalid JWT format (corrupted token)", HttpStatus.UNAUTHORIZED);
    }

}
package com.citicor.auth.controller;

import com.citicor.auth.dto.*;
import com.citicor.auth.exception.AuthException;
import com.citicor.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RefreshScope
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            service.register(req);
            return ResponseEntity.ok("User registered");
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(service.login(req));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(service.refreshToken(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest req) {
        service.logout(req.getRefreshToken());
        return ResponseEntity.ok("Logged out successfully");
    }
    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUser verifyUserDto) {
        try {
            service.verifyUser(verifyUserDto);
            return ResponseEntity.ok("Account verified successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestParam String email) {
        try {
            service.resendVerificationCode(email);
            return ResponseEntity.ok("Verification code sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
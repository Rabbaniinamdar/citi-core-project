package com.citicor.auth.controller;

import com.citicor.auth.dto.*;
import com.citicor.auth.exception.AuthException;
import com.citicor.auth.service.AuthService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RefreshScope
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @RateLimiter(name = "register", fallbackMethod = "rateLimitFallback")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            service.register(req);
            return ResponseEntity.ok("User registered");
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getMessage());
        }
    }

    @RateLimiter(name = "login", fallbackMethod = "rateLimitFallback")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(service.login(req));
    }

    @RateLimiter(name = "verify", fallbackMethod = "rateLimitFallback")
    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUser verifyUserDto) {
        try {
            service.verifyUser(verifyUserDto);
            return ResponseEntity.ok("Account verified successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @RateLimiter(name = "resend", fallbackMethod = "rateLimitFallback")
    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestParam String email) {
        try {
            service.resendVerificationCode(email);
            return ResponseEntity.ok("Verification code sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        try {
            service.forgotPassword(req);
            // Always return success to prevent account enumeration
            return ResponseEntity.ok("If the email exists, a password reset link has been sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        try {
            service.resetPassword(req);
            return ResponseEntity.ok("Password reset successful");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            // Extract user ID from JWT token (in real implementation, use SecurityContext)
            // For now, extract from token manually
            String token = authHeader.replace("Bearer ", "");
            Long userId = service.extractUserIdFromToken(token);
            service.changePassword(req, userId);
            return ResponseEntity.ok("Password changed successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<?> enableEmailOtpMfa(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = service.extractUserIdFromToken(token);
            service.enableEmailOtpMfa(userId);
            return ResponseEntity.ok("Email OTP MFA enabled successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/mfa/verify-challenge")
    public ResponseEntity<LoginResponse> verifyMfaChallenge(@Valid @RequestBody VerifyMfaChallengeRequest req) {
        return ResponseEntity.ok(service.verifyMfaChallenge(req));
    }

    @PostMapping("/mfa/resend-otp")
    public ResponseEntity<?> resendMfaOtp(@RequestParam String challengeId) {
        try {
            service.resendMfaOtp(challengeId);
            return ResponseEntity.ok("OTP resent successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/mfa/disable/initiate")
    public ResponseEntity<?> initiateMfaDisable(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = service.extractUserIdFromToken(
                authHeader.replace("Bearer ", "")
        );

        String challengeId = service.initiateMfaDisable(userId);
        return ResponseEntity.ok(Map.of("challengeId", challengeId));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableMfa(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody VerifyMfaChallengeRequest req
    ) {
        Long userId = service.extractUserIdFromToken(
                authHeader.replace("Bearer ", "")
        );

        service.disableMfa(userId, req.getChallengeId(), req.getOtpCode());
        return ResponseEntity.ok("MFA disabled successfully");
    }
    private ResponseEntity<?> rateLimitFallback(String method, Throwable ex) {
        return ResponseEntity.status(429).body("Too many requests. Please try again later.");
    }
}
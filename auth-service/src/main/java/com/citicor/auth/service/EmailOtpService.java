package com.citicor.auth.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class EmailOtpService {

    private static final String OTP_CHARS = "0123456789";

    @Value("${citicore.auth.mfa.email-otp.length:6}")
    private int otpLength;

    @Value("${citicore.auth.mfa.email-otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${citicore.auth.mfa.email-otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${citicore.auth.mfa.email-otp.resend-cooldown-minutes:1}")
    private int resendCooldownMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (otpLength < 4 || otpLength > 8) {
            throw new IllegalStateException("OTP length must be between 4 and 8");
        }
    }

    public String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(OTP_CHARS.charAt(secureRandom.nextInt(OTP_CHARS.length())));
        }
        return sb.toString();
    }

    public String hashOtp(String otp) {
        // Use BCrypt to hash the OTP for storage
        return org.springframework.security.crypto.bcrypt.BCrypt.hashpw(otp, org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
    }

    public boolean verifyOtp(String rawOtp, String hashedOtp) {
        return org.springframework.security.crypto.bcrypt.BCrypt.checkpw(rawOtp, hashedOtp);
    }

    public int getOtpLength() {
        return otpLength;
    }

    public int getExpiryMinutes() {
        return expiryMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getResendCooldownMinutes() {
        return resendCooldownMinutes;
    }

    public LocalDateTime calculateExpiry() {
        return LocalDateTime.now().plusMinutes(expiryMinutes);
    }
}
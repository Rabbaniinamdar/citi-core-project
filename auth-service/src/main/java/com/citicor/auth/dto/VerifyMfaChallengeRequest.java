package com.citicor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyMfaChallengeRequest {

    @NotBlank(message = "Challenge ID is required")
    @Size(min = 32, max = 64, message = "Invalid challenge ID")
    private String challengeId;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    private String otpCode;

    public VerifyMfaChallengeRequest() {
    }

    public VerifyMfaChallengeRequest(String challengeId, String otpCode) {
        this.challengeId = challengeId;
        this.otpCode = otpCode;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
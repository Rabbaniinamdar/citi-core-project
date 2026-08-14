package com.citicore.events.otp;

public class VerificationOtpEvent {

    private Long userId;
    private String email;
    private String verificationCode;
    private String fullName;
    private Integer expiryMinutes;

    // No-args constructor
    public VerificationOtpEvent() {
    }

    // All-args constructor
    public VerificationOtpEvent(Long userId, String email, String verificationCode,
                                String fullName, Integer expiryMinutes) {
        this.userId = userId;
        this.email = email;
        this.verificationCode = verificationCode;
        this.fullName = fullName;
        this.expiryMinutes = expiryMinutes;
    }

    // Getters
    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getExpiryMinutes() {
        return expiryMinutes;
    }

    // Setters
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setExpiryMinutes(Integer expiryMinutes) {
        this.expiryMinutes = expiryMinutes;
    }

    // Builder (manual)
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String email;
        private String verificationCode;
        private String fullName;
        private Integer expiryMinutes;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder verificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder expiryMinutes(Integer expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
            return this;
        }

        public VerificationOtpEvent build() {
            return new VerificationOtpEvent(userId, email, verificationCode, fullName, expiryMinutes);
        }
    }
}
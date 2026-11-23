package com.citicor.auth.dto;

import java.util.List;

public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private boolean mfaRequired;
    private String challengeId;
    private List<String> mfaMethods;
    private String message;

    public LoginResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.mfaRequired = false;
    }

    public LoginResponse(boolean mfaRequired, String challengeId, List<String> mfaMethods, String message) {
        this.mfaRequired = mfaRequired;
        this.challengeId = challengeId;
        this.mfaMethods = mfaMethods;
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public List<String> getMfaMethods() {
        return mfaMethods;
    }

    public void setMfaMethods(List<String> mfaMethods) {
        this.mfaMethods = mfaMethods;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
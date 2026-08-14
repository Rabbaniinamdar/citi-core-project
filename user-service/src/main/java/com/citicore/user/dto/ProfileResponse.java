package com.citicore.user.dto;

import com.citicore.user.entity.KycStatus;
import com.citicore.user.entity.UserProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for user profile data.
 *
 * Returned by:
 *   POST /api/v1/user/profile     → createProfile()
 *   GET  /api/v1/user/profile     → getMyProfile()
 *   PUT  /api/v1/user/profile     → updateProfile()
 *   GET  /api/v1/kyc/admin/pending → getPendingKycUsers()
 *   POST /api/v1/kyc/admin/review  → reviewKyc()
 *
 * Uses a static factory method ProfileResponse.from(UserProfile)
 * to keep mapping logic in one place.
 */
public class ProfileResponse {

    private Long id;
    private Long authUserId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private KycStatus kycStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ProfileResponse() {}

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Maps a UserProfile entity to a ProfileResponse DTO.
     * Centralises the mapping so controllers don't do manual field assignment.
     */
    public static ProfileResponse from(UserProfile profile) {
        ProfileResponse response = new ProfileResponse();
        response.id          = profile.getId();
        response.authUserId  = profile.getAuthUserId();
        response.firstName   = profile.getFirstName();
        response.lastName    = profile.getLastName();
        response.fullName    = profile.getFullName();
        response.email       = profile.getEmail();
        response.phoneNumber = profile.getPhoneNumber();
        response.dateOfBirth = profile.getDateOfBirth();
        response.address     = profile.getAddress();
        response.city        = profile.getCity();
        response.state       = profile.getState();
        response.pincode     = profile.getPincode();
        response.kycStatus   = profile.getKycStatus();
        response.createdAt   = profile.getCreatedAt();
        response.updatedAt   = profile.getUpdatedAt();
        return response;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                { return id; }
    public Long getAuthUserId()        { return authUserId; }
    public String getFirstName()       { return firstName; }
    public String getLastName()        { return lastName; }
    public String getFullName()        { return fullName; }
    public String getEmail()           { return email; }
    public String getPhoneNumber()     { return phoneNumber; }
    public LocalDate getDateOfBirth()  { return dateOfBirth; }
    public String getAddress()         { return address; }
    public String getCity()            { return city; }
    public String getState()           { return state; }
    public String getPincode()         { return pincode; }
    public KycStatus getKycStatus()    { return kycStatus; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
}
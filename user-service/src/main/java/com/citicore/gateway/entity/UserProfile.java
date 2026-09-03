package com.citicore.gateway.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stores user profile information collected during onboarding.
 *
 * Linked to the auth_users table (auth-service) via authUserId.
 * This is a one-to-one relationship maintained by convention
 * (no JPA join — auth-service owns the auth record).
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * References the user in auth-service (auth_users.id).
     * Unique — one profile per authenticated user.
     */
    @Column(name = "auth_user_id", unique = true, nullable = false)
    private Long authUserId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "pincode")
    private String pincode;

    /**
     * KYC verification status.
     * Checked by account-service via GET /api/v1/user/kyc-status/{userId}
     * before allowing account creation.
     *
     * PENDING → UNDER_REVIEW → APPROVED or REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public UserProfile() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                 { return id; }
    public Long getAuthUserId()         { return authUserId; }
    public String getFirstName()        { return firstName; }
    public String getLastName()         { return lastName; }
    public String getEmail()            { return email; }
    public String getPhoneNumber()      { return phoneNumber; }
    public LocalDate getDateOfBirth()   { return dateOfBirth; }
    public String getAddress()          { return address; }
    public String getCity()             { return city; }
    public String getState()            { return state; }
    public String getPincode()          { return pincode; }
    public KycStatus getKycStatus()     { return kycStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setAuthUserId(Long v)       { this.authUserId = v; }
    public void setFirstName(String v)      { this.firstName = v; }
    public void setLastName(String v)       { this.lastName = v; }
    public void setEmail(String v)          { this.email = v; }
    public void setPhoneNumber(String v)    { this.phoneNumber = v; }
    public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; }
    public void setAddress(String v)        { this.address = v; }
    public void setCity(String v)           { this.city = v; }
    public void setState(String v)          { this.state = v; }
    public void setPincode(String v)        { this.pincode = v; }
    public void setKycStatus(KycStatus v)   { this.kycStatus = v; }
}
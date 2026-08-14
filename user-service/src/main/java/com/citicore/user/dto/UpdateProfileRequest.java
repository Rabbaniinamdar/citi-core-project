package com.citicore.user.dto;

import java.time.LocalDate;

/**
 * DTO for updating mutable profile fields.
 *
 * All fields are optional — only non-null fields are applied.
 * This allows partial updates (PATCH-style behaviour via PUT).
 *
 * Fields that CANNOT be changed:
 *   - email       (identity — linked to auth-service)
 *   - authUserId  (system-managed)
 *
 * UserService.updateProfile() checks each field for null before setting:
 *   if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
 */
public class UpdateProfileRequest {

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String pincode;

    public UpdateProfileRequest() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getFirstName()      { return firstName; }
    public String getLastName()       { return lastName; }
    public String getPhoneNumber()    { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddress()        { return address; }
    public String getCity()           { return city; }
    public String getState()          { return state; }
    public String getPincode()        { return pincode; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setFirstName(String v)      { this.firstName = v; }
    public void setLastName(String v)       { this.lastName = v; }
    public void setPhoneNumber(String v)    { this.phoneNumber = v; }
    public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; }
    public void setAddress(String v)        { this.address = v; }
    public void setCity(String v)           { this.city = v; }
    public void setState(String v)          { this.state = v; }
    public void setPincode(String v)        { this.pincode = v; }
}
package com.citicore.gateway.dto;

import java.time.LocalDate;

public class CreateProfileRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String pincode;

    public CreateProfileRequest() {}

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates required fields.
     * Called in UserService.createProfile() before saving.
     */
    public boolean isInvalid() {
        return firstName == null || firstName.isBlank()
                || lastName == null || lastName.isBlank()
                || email == null || email.isBlank()
                || !email.contains("@");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getFirstName()      { return firstName; }
    public String getLastName()       { return lastName; }
    public String getEmail()          { return email; }
    public String getPhoneNumber()    { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddress()        { return address; }
    public String getCity()           { return city; }
    public String getState()          { return state; }
    public String getPincode()        { return pincode; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setFirstName(String v)      { this.firstName = v; }
    public void setLastName(String v)       { this.lastName = v; }
    public void setEmail(String v)          { this.email = v; }
    public void setPhoneNumber(String v)    { this.phoneNumber = v; }
    public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; }
    public void setAddress(String v)        { this.address = v; }
    public void setCity(String v)           { this.city = v; }
    public void setState(String v)          { this.state = v; }
    public void setPincode(String v)        { this.pincode = v; }
}
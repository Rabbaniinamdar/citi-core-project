package com.citicore.user.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UserProfileRequest(

        // 👤 Personal Details
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotNull(message = "Date of birth is required")
        @Past(message = "DOB must be in the past")
        LocalDate dob,

        @NotBlank(message = "Gender is required")
        String gender,

        @NotBlank(message = "Father name is required")
        String fatherName,

        // 📞 Contact
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
        String phone,

        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid alternate phone")
        String alternatePhone,

        // 📍 Address (structured)
        @NotBlank(message = "Address line1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "State is required")
        String state,

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid pincode")
        String pincode,

        @NotBlank(message = "Country is required")
        String country,

        // 🪪 KYC (user inputs, verification happens later)
        @NotBlank(message = "PAN is required")
        @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format")
        String pan,

        @NotBlank(message = "Aadhaar is required")
        @Pattern(regexp = "\\d{12}", message = "Aadhaar must be 12 digits")
        String aadhar
) {}
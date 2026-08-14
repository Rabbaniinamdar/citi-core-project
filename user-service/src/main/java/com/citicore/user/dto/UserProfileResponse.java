package com.citicore.user.dto;

public record UserProfileResponse(

        // Public identifier (NOT DB id)
        String customerId,

        // 👤 Personal Details
        String fullName,
        String gender,

        // 📞 Contact
        String email,
        String phone,
        String alternatePhone,

        // 📍 Address
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,

        // 🪪 KYC status (safe to expose)
        String kycStatus, boolean panVerified,
        boolean aadharVerified,

        // 🏦 Account status
        String accountStatus
) {}
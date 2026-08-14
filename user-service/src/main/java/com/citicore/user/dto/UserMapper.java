//package com.citicore.user.dto;
//
//import com.citicore.user.entity.UserProfile;
//
//public class UserMapper {
//
//    public static UserProfile toEntity(UserProfileRequest req) {
//        UserProfile u = new UserProfile();
//        u.setFullName(req.fullName());
//        u.setDob(req.dob());
//        return u;
//    }
//
//    public static UserProfileResponse toResponse(UserProfile user) {
//        return new UserProfileResponse(
//                user.getCustomerId(),
//                user.getFullName(),
//                user.getGender(),
//                user.getEmail(),
//                user.getPhoneNumber(),
//                user.getAlternatePhone(),
//                user.getAddress(),
//                user.getCity(),
//                user.getState(),
//                user.getPincode(),
//                user.getCountry(),
//                user.getKycStatus(),
//                user.isPanVerified(),
//                user.isAadharVerified(),
//                user.getAccountStatus()
//        );
//    }
//}
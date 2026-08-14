package com.citicore.user.repository;

import com.citicore.user.entity.KycStatus;
import com.citicore.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * Find by authUserId — primary lookup for authenticated users.
     * Every API call uses this to find the profile of the JWT user.
     */
    Optional<UserProfile> findByAuthUserId(Long authUserId);

    /**
     * Find by email — used for duplicate check during profile creation.
     */
    Optional<UserProfile> findByEmail(String email);

    /**
     * Find by phone — used for duplicate check.
     */
    Optional<UserProfile> findByPhoneNumber(String phoneNumber);

    /**
     * Find all profiles with a given KYC status.
     * Used by admin endpoints to list users pending review.
     */
    List<UserProfile> findByKycStatus(KycStatus kycStatus);

    /**
     * Check if profile already exists for this user.
     * Prevents duplicate profile creation.
     */
    boolean existsByAuthUserId(Long authUserId);

    /**
     * Check if email is already registered.
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone is already registered.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
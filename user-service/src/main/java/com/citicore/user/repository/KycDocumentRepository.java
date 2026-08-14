package com.citicore.user.repository;

import com.citicore.user.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    /**
     * Fetch all documents uploaded by a specific user.
     * Used to display document list in the KYC dashboard.
     */
    List<KycDocument> findByAuthUserId(Long authUserId);

    /**
     * Fetch a specific document type for a user.
     * Used to check if user has already uploaded a particular document.
     * Example: has the user already uploaded their AADHAAR?
     */
    Optional<KycDocument> findByAuthUserIdAndDocumentType(Long authUserId,
                                                          String documentType);

    /**
     * Check if a specific document type already exists for this user.
     * Prevents duplicate uploads of the same document type.
     */
    boolean existsByAuthUserIdAndDocumentType(Long authUserId, String documentType);

    /**
     * Delete all documents for a user.
     * Used when user re-submits KYC after rejection.
     */
    void deleteByAuthUserId(Long authUserId);
}
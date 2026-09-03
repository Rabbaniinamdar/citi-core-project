package com.citicore.gateway.service;

import com.citicore.gateway.dto.*;
import com.citicore.gateway.entity.*;
import com.citicore.gateway.exception.*;
import com.citicore.gateway.kafka.KafkaProducerService;
import com.citicore.gateway.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserProfileRepository  userProfileRepository;
    private final KycDocumentRepository  kycDocumentRepository;
    private final KafkaProducerService   kafkaProducerService;

    public UserService(
            UserProfileRepository userProfileRepository,
            KycDocumentRepository kycDocumentRepository,
            KafkaProducerService kafkaProducerService) {
        this.userProfileRepository = userProfileRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.kafkaProducerService  = kafkaProducerService;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROFILE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a user profile for a newly registered user.
     *
     * Called after successful auth-service registration.
     * The authUserId comes from the JWT token so users can only
     * create their own profile — no IDOR risk.
     *
     * Guards:
     *   - Duplicate profile check (one profile per authUserId)
     *   - Duplicate email check
     *   - Duplicate phone check
     */
    @Transactional
    public UserProfile createProfile(CreateProfileRequest request, Long authUserId) {

        // ── Duplicate checks ──────────────────────────────────────────────────
        if (userProfileRepository.existsByAuthUserId(authUserId)) {
            throw new DuplicateProfileException(
                    "Profile already exists for user: " + authUserId);
        }
        if (userProfileRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(
                    "Email already registered: " + request.getEmail());
        }
        if (request.getPhoneNumber() != null
                && userProfileRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicatePhoneException(
                    "Phone number already registered: " + request.getPhoneNumber());
        }

        // ── Build and save profile ────────────────────────────────────────────
        UserProfile profile = new UserProfile();
        profile.setAuthUserId(authUserId);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setEmail(request.getEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setAddress(request.getAddress());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPincode(request.getPincode());
        profile.setKycStatus(KycStatus.PENDING);  // always starts as PENDING

        userProfileRepository.save(profile);

        System.out.println("✅ [PROFILE CREATED] authUserId=" + authUserId
                + " | email=" + request.getEmail());

        return profile;
    }

    /**
     * Returns the profile of the authenticated user.
     */
    public UserProfile getMyProfile(Long authUserId) {
        return userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));
    }

    /**
     * Updates mutable profile fields.
     * Email and authUserId cannot be changed.
     */
    @Transactional
    public UserProfile updateProfile(UpdateProfileRequest request, Long authUserId) {
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));

        if (request.getFirstName()   != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName()    != null) profile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress()     != null) profile.setAddress(request.getAddress());
        if (request.getCity()        != null) profile.setCity(request.getCity());
        if (request.getState()       != null) profile.setState(request.getState());
        if (request.getPincode()     != null) profile.setPincode(request.getPincode());

        return userProfileRepository.save(profile);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // KYC — STATUS CHECK (called by account-service via Feign)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the user's KYC is APPROVED.
     *
     * Called by account-service's UserClient (Feign) before creating an account:
     *   GET /api/v1/user/kyc-status/{userId}
     *
     * This is the gate that prevents unverified users from opening accounts.
     */
    public boolean getKycStatus(Long authUserId) {
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));
        return profile.getKycStatus() == KycStatus.APPROVED;
    }

    /**
     * Returns the full KYC status enum value.
     * Used by the client app to show the correct UI state.
     */
    public KycStatus getKycStatusDetail(Long authUserId) {
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));
        return profile.getKycStatus();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // KYC — DOCUMENT SUBMISSION
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Saves KYC document metadata after S3 upload.
     *
     * The actual file upload to S3 is handled by KycService (via AWS SDK).
     * This method saves the metadata (S3 key, document type, etc.)
     * and transitions KYC status from PENDING → UNDER_REVIEW.
     *
     * If the user previously had REJECTED status, this allows re-submission.
     */
    @Transactional
    public KycDocument saveKycDocument(Long authUserId, String documentType,
                                       String s3Key, String fileName,
                                       String contentType, Long fileSize) {

        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));

        if (profile.getKycStatus() == KycStatus.APPROVED) {
            throw new KycAlreadyApprovedException(
                    "KYC already approved. No re-submission needed.");
        }

        // If document type already exists, replace it (re-submission)
        kycDocumentRepository.findByAuthUserIdAndDocumentType(authUserId, documentType)
                .ifPresent(existing -> kycDocumentRepository.delete(existing));

        // Save new document metadata
        KycDocument document = new KycDocument();
        document.setAuthUserId(authUserId);
        document.setDocumentType(documentType);
        document.setS3Key(s3Key);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setFileSize(fileSize);

        kycDocumentRepository.save(document);

        // Transition to UNDER_REVIEW if not already
        if (profile.getKycStatus() != KycStatus.UNDER_REVIEW) {
            profile.setKycStatus(KycStatus.UNDER_REVIEW);
            userProfileRepository.save(profile);
        }

        System.out.println("📄 [KYC DOC SAVED] userId=" + authUserId
                + " | type=" + documentType
                + " | file=" + fileName);

        return document;
    }

    /**
     * Returns all KYC documents uploaded by the authenticated user.
     */
    public List<KycDocument> getMyDocuments(Long authUserId) {
        return kycDocumentRepository.findByAuthUserId(authUserId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // KYC — ADMIN REVIEW
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Admin approves or rejects KYC for a user.
     *
     * On status change:
     *   - Updates KycStatus in user_profiles
     *   - Publishes KycEvent to kyc-topic
     *   - Notification-service sends appropriate email
     *
     * Only transitions UNDER_REVIEW → APPROVED or UNDER_REVIEW → REJECTED.
     * APPROVED users cannot be rejected again without re-submission.
     */
    @Transactional
    public UserProfile reviewKyc(Long authUserId, KycStatus newStatus) {

        if (newStatus != KycStatus.APPROVED && newStatus != KycStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Review can only set status to APPROVED or REJECTED");
        }

        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ProfileNotFoundException(
                        "Profile not found for user: " + authUserId));

        if (profile.getKycStatus() == KycStatus.APPROVED) {
            throw new KycAlreadyApprovedException(
                    "KYC already approved for user: " + authUserId);
        }

        if (profile.getKycStatus() != KycStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Cannot review KYC with status: " + profile.getKycStatus()
                            + ". User must submit documents first.");
        }

        profile.setKycStatus(newStatus);
        userProfileRepository.save(profile);

        // Publish event → notification-service sends approval/rejection email
        kafkaProducerService.publishKycEvent(
                authUserId,
                profile.getEmail(),
                newStatus.name()
        );

        System.out.println("✅ [KYC REVIEWED] userId=" + authUserId
                + " | newStatus=" + newStatus);

        return profile;
    }

    /**
     * Returns all users currently UNDER_REVIEW.
     * Used by admin dashboard to show pending KYC queue.
     */
    public List<UserProfile> getPendingKycUsers() {
        return userProfileRepository.findByKycStatus(KycStatus.UNDER_REVIEW);
    }

    /**
     * Returns all users with a given KYC status.
     * Used by admin for reporting and filtering.
     */
    public List<UserProfile> getUsersByKycStatus(KycStatus status) {
        return userProfileRepository.findByKycStatus(status);
    }
}
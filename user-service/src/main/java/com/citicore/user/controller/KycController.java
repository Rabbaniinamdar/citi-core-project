package com.citicore.user.controller;

import com.citicore.user.dto.ApiResponse;
import com.citicore.user.dto.KycReviewRequest;
import com.citicore.user.dto.ProfileResponse;
import com.citicore.user.entity.AuthUser;
import com.citicore.user.entity.KycDocument;
import com.citicore.user.entity.KycStatus;
import com.citicore.user.entity.UserProfile;
import com.citicore.user.service.KycService;
import com.citicore.user.service.UserService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc")
@RefreshScope
public class KycController {

    private final KycService  kycService;
    private final UserService userService;

    public KycController(KycService kycService, UserService userService) {
        this.kycService  = kycService;
        this.userService = userService;
    }

    /**
     * POST /api/v1/kyc/upload
     * Uploads a KYC document to S3 and saves metadata.
     *
     * Content-Type: multipart/form-data
     * Params:
     *   documentType  — e.g. AADHAAR, PAN, PASSPORT (form param)
     *   file          — the document file (image/pdf, max 5MB)
     *
     * On success:
     *   - File stored in S3 at: kyc/{userId}/{documentType}/{uuid}_{filename}
     *   - Metadata saved in kyc_documents table
     *   - KYC status transitions to UNDER_REVIEW (if not already)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadDocument(
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        AuthUser authUser = getAuthUser();

        try {
            KycDocument document = kycService.uploadDocument(
                    authUser.getId(), documentType, file);

            return ResponseEntity.ok(ApiResponse.success(
                    "Document uploaded successfully. KYC is now under review.",
                    Map.of(
                            "documentType", document.getDocumentType(),
                            "fileName",     document.getFileName(),
                            "kycStatus",    "UNDER_REVIEW"
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.failure("Upload failed: " + e.getMessage(), null));
        }
    }

    /**
     * GET /api/v1/kyc/my-documents
     * Returns a list of all KYC documents uploaded by the authenticated user.
     * Does NOT return the actual file — use /document-url/{id} for that.
     */
    @GetMapping("/my-documents")
    public ResponseEntity<ApiResponse<List<KycDocument>>> getMyDocuments() {
        AuthUser authUser = getAuthUser();
        List<KycDocument> documents = userService.getMyDocuments(authUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Documents fetched", documents));
    }

    /**
     * GET /api/v1/kyc/document-url/{documentId}
     * Generates a 15-minute pre-signed S3 URL for secure document download.
     *
     * Used by admin to view uploaded documents during KYC review.
     * Pre-signed URL expires after 15 minutes for security.
     */
    @GetMapping("/document-url/{documentId}")
    public ResponseEntity<ApiResponse<String>> getDocumentUrl(
            @PathVariable Long documentId) {

        List<KycDocument> allDocs = userService.getMyDocuments(getAuthUser().getId());

        KycDocument document = allDocs.stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Document not found: " + documentId));

        String presignedUrl = kycService.generatePresignedUrl(document.getS3Key());

        return ResponseEntity.ok(ApiResponse.success(
                "Pre-signed URL generated (expires in 15 minutes)",
                presignedUrl
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN ENDPOINTS
    // In production, secure with: @PreAuthorize("hasRole('ADMIN')")
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/kyc/admin/pending
     * Returns all users currently UNDER_REVIEW.
     * Used by admin dashboard to display the KYC review queue.
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<ApiResponse<List<ProfileResponse>>> getPendingKycUsers() {
        List<UserProfile> profiles = userService.getPendingKycUsers();
        List<ProfileResponse> response = profiles.stream()
                .map(ProfileResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(
                "Pending KYC users fetched", response));
    }

    /**
     * GET /api/v1/kyc/admin/users?status=APPROVED
     * Returns all users with a given KYC status.
     * Used by admin for reporting and compliance.
     *
     * @param status PENDING / UNDER_REVIEW / APPROVED / REJECTED
     */
    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<ProfileResponse>>> getUsersByStatus(
            @RequestParam KycStatus status) {
        List<UserProfile> profiles = userService.getUsersByKycStatus(status);
        List<ProfileResponse> response = profiles.stream()
                .map(ProfileResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(
                "Users fetched for status: " + status, response));
    }

    /**
     * POST /api/v1/kyc/admin/review/{userId}
     * Admin approves or rejects a user's KYC.
     *
     * Body: { "status": "APPROVED" }  or  { "status": "REJECTED" }
     *
     * On success:
     *   - Updates kycStatus in user_profiles
     *   - Publishes KycEvent to kyc-topic
     *   - Notification-service sends approval/rejection email
     *
     * Transition rules enforced in UserService:
     *   - Only UNDER_REVIEW → APPROVED or REJECTED allowed
     *   - Already APPROVED cannot be rejected
     */
    @PostMapping("/admin/review/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> reviewKyc(
            @PathVariable Long userId,
            @RequestBody KycReviewRequest request) {

        UserProfile updated = userService.reviewKyc(userId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(
                "KYC " + request.getStatus().name().toLowerCase() + " successfully",
                ProfileResponse.from(updated)
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private AuthUser getAuthUser() {
        return (AuthUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
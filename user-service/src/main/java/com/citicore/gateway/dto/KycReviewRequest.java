package com.citicore.gateway.dto;

import com.citicore.gateway.entity.KycStatus;

/**
 * Request DTO for admin KYC review.
 *
 * Used by:
 *   POST /api/v1/kyc/admin/review/{userId}
 *
 * Only APPROVED or REJECTED are valid values.
 * UserService.reviewKyc() enforces this:
 *   if (newStatus != KycStatus.APPROVED && newStatus != KycStatus.REJECTED)
 *       throw new IllegalArgumentException(...)
 *
 * Valid request body:
 *   { "status": "APPROVED" }
 *   { "status": "REJECTED" }
 *
 * Invalid (will throw):
 *   { "status": "PENDING" }
 *   { "status": "UNDER_REVIEW" }
 */
public class KycReviewRequest {

    private KycStatus status;

    public KycReviewRequest() {}

    public KycStatus getStatus() { return status; }
    public void setStatus(KycStatus status) { this.status = status; }
}
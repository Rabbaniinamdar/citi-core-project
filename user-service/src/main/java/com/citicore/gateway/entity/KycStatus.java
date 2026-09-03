package com.citicore.gateway.entity;

public enum KycStatus {

    /**
     * User has not submitted KYC documents yet.
     * Cannot create bank accounts.
     */
    PENDING,

    /**
     * KYC documents submitted, awaiting admin/automated review.
     * Cannot create bank accounts yet.
     */
    UNDER_REVIEW,

    /**
     * KYC verified successfully.
     * User can now create Savings or Current accounts.
     * Notification service sends approval email.
     */
    APPROVED,

    /**
     * KYC rejected — documents invalid or mismatch.
     * User must re-submit documents.
     * Notification service sends rejection email.
     */
    REJECTED
}
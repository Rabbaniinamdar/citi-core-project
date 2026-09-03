package com.citicore.gateway.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores metadata about KYC documents uploaded by a user.
 *
 * The actual file is stored in AWS S3.
 * This entity holds the S3 key (path) for retrieval.
 *
 * A user can upload multiple documents (Aadhaar, PAN, Passport etc.)
 * Each document is stored as a separate row.
 */
@Entity
@Table(name = "kyc_documents")
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    /**
     * Type of document uploaded.
     * Examples: AADHAAR, PAN, PASSPORT, DRIVING_LICENSE, VOTER_ID
     */
    @Column(name = "document_type", nullable = false)
    private String documentType;

    /**
     * S3 object key — used to generate a pre-signed URL for download.
     * Format: kyc/{userId}/{documentType}/{filename}
     * Example: kyc/42/AADHAAR/aadhaar_front.jpg
     */
    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    /**
     * Original filename uploaded by the user.
     * Stored for display purposes only.
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** MIME type — image/jpeg, image/png, application/pdf */
    @Column(name = "content_type")
    private String contentType;

    /** File size in bytes */
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    public void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }

    public KycDocument() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()               { return id; }
    public Long getAuthUserId()       { return authUserId; }
    public String getDocumentType()   { return documentType; }
    public String getS3Key()          { return s3Key; }
    public String getFileName()       { return fileName; }
    public String getContentType()    { return contentType; }
    public Long getFileSize()         { return fileSize; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setAuthUserId(Long v)      { this.authUserId = v; }
    public void setDocumentType(String v)  { this.documentType = v; }
    public void setS3Key(String v)         { this.s3Key = v; }
    public void setFileName(String v)      { this.fileName = v; }
    public void setContentType(String v)   { this.contentType = v; }
    public void setFileSize(Long v)        { this.fileSize = v; }
}
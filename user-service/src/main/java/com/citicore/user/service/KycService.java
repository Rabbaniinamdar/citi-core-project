package com.citicore.user.service;

import com.citicore.user.entity.KycDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Handles KYC document upload to AWS S3 and pre-signed URL generation.
 *
 * S3 Key format: kyc/{authUserId}/{documentType}/{UUID}_{originalFilename}
 * Example:       kyc/42/AADHAAR/f3a1b2c4_aadhaar_front.jpg
 *
 * Pre-signed URLs expire after 15 minutes — used for temporary
 * secure access to private S3 documents without exposing credentials.
 */
@Service
public class KycService {

    private final S3Client      s3Client;
    private final S3Presigner   s3Presigner;
    private final UserService   userService;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public KycService(S3Client s3Client,
                      S3Presigner s3Presigner,
                      UserService userService) {
        this.s3Client    = s3Client;
        this.s3Presigner = s3Presigner;
        this.userService = userService;
    }

    /**
     * Uploads a KYC document to S3 and saves metadata via UserService.
     *
     * @param authUserId   authenticated user's ID
     * @param documentType e.g. "AADHAAR", "PAN", "PASSPORT"
     * @param file         multipart file from the HTTP request
     * @return saved KycDocument entity with S3 key
     */
    public KycDocument uploadDocument(Long authUserId,
                                      String documentType,
                                      MultipartFile file) throws IOException {

        validateFile(file);

        // Build S3 key: kyc/{userId}/{type}/{uuid}_{originalName}
        String uniqueId     = UUID.randomUUID().toString().substring(0, 8);
        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        String s3Key = String.format("kyc/%d/%s/%s_%s",
                authUserId,
                documentType.toUpperCase(),
                uniqueId,
                sanitizedName
        );

        // ── Upload to S3 ──────────────────────────────────────────────────────
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        System.out.println("☁️ [S3 UPLOAD] key=" + s3Key + " | size=" + file.getSize());

        // ── Save metadata to DB via UserService ───────────────────────────────
        return userService.saveKycDocument(
                authUserId,
                documentType.toUpperCase(),
                s3Key,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
    }

    /**
     * Generates a pre-signed URL for temporary secure access to an S3 document.
     * URL expires after 15 minutes.
     *
     * @param s3Key the S3 object key stored in KycDocument
     * @return pre-signed HTTPS URL string
     */
    public String generatePresignedUrl(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getRequest)
                .build();

        PresignedGetObjectRequest presignedRequest =
                s3Presigner.presignGetObject(presignRequest);

        String url = presignedRequest.url().toString();
        System.out.println("🔗 [PRESIGN URL] key=" + s3Key + " | expires=15min");
        return url;
    }

    /**
     * Deletes a document from S3.
     * Called when a user re-submits a document after rejection.
     *
     * @param s3Key the S3 object key to delete
     */
    public void deleteDocument(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build());
        System.out.println("🗑️ [S3 DELETE] key=" + s3Key);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/")
                && !contentType.equals("application/pdf"))) {
            throw new IllegalArgumentException(
                    "Only image files (JPEG, PNG) and PDFs are allowed. Got: " + contentType);
        }

        // 5 MB limit
        long maxSizeBytes = 5 * 1024 * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "File size exceeds 5MB limit. Got: " + file.getSize() + " bytes");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "document";
        // Remove path traversal characters and spaces
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }
}
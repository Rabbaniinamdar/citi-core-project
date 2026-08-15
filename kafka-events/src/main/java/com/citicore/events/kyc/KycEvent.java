package com.citicore.events.kyc;


public class KycEvent {
    private KycEventType eventType;
    private Long documentId;
    private Long userId;
    private String email;
    private String filePath;
    private String status;

    KycEvent(){}

    public KycEvent(KycEventType eventType, Long documentId, Long userId, String email, String filePath) {
        this.eventType = eventType;
        this.documentId = documentId;
        this.userId = userId;
        this.email = email;
        this.filePath = filePath;
    }

    public KycEvent(Long userId, String email, String status) {
        this.userId = userId;
        this.email = email;
        this.status = status;
    }

    public KycEventType getEventType() {
        return eventType;
    }

    public void setEventType(KycEventType eventType) {
        this.eventType = eventType;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
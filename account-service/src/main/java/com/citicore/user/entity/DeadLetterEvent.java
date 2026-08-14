package com.citicore.user.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_id")
    private Integer partitionId;

    @Column(name = "offset_value")
    private Long offsetValue;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "exception_class")
    private String exceptionClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DLQStatus status = DLQStatus.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public DeadLetterEvent() {}

    public DeadLetterEvent(String topic, Integer partitionId, Long offsetValue,
                           String payload, String errorMessage, String exceptionClass) {
        this.topic = topic;
        this.partitionId = partitionId;
        this.offsetValue = offsetValue;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.exceptionClass = exceptionClass;
        this.status = DLQStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId()                          { return id; }
    public String getTopic()                     { return topic; }
    public Integer getPartitionId()              { return partitionId; }
    public Long getOffsetValue()                 { return offsetValue; }
    public String getPayload()                   { return payload; }
    public String getErrorMessage()              { return errorMessage; }
    public String getExceptionClass()            { return exceptionClass; }
    public DLQStatus getStatus()                 { return status; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getResolvedAt()         { return resolvedAt; }
    public void setStatus(DLQStatus status)      { this.status = status; }
    public void setResolvedAt(LocalDateTime t)   { this.resolvedAt = t; }
}
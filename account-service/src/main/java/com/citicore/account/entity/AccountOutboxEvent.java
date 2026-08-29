package com.citicore.account.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_outbox")
public class AccountOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * UUID — prevents duplicate inserts if the same event is
     * accidentally saved twice. Unique constraint on this column.
     */
    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    /**
     * Account number — used as the Kafka partition key.
     * Guarantees all events for the same account land on the
     * same partition and are processed in order.
     */
    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    /**
     * Target Kafka topic.
     * Examples:
     *   debit-success-topic
     *   debit-failed-topic
     *   credit-success-topic
     *   credit-failed-topic
     *   reversal-success-topic
     *   account-events-topic
     */
    @Column(name = "topic", nullable = false)
    private String topic;

    /**
     * Raw JSON string of the event payload.
     *
     * Stored as TEXT so any size event fits.
     * AccountOutboxPublisher sends this via KafkaTemplate<String, String>
     * with StringSerializer — no double encoding.
     *
     * NEVER use KafkaTemplate<String, Object> with JsonSerializer here —
     * it would wrap the string in extra quotes:
     *   DB:   {"txnRef":"CITI-001"}
     *   Wire: "{\"txnRef\":\"CITI-001\"}"  ← double encoded, consumers break ❌
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Lifecycle status of this outbox event.
     * PENDING  → not yet published to Kafka
     * SENT     → successfully published
     * FAILED   → publish attempt failed (Kafka down), will retry on next poll
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public AccountOutboxEvent() {}

    public AccountOutboxEvent(String eventId, String accountNumber, String topic,
                              String payload, OutboxStatus status, LocalDateTime createdAt) {
        this.eventId       = eventId;
        this.accountNumber = accountNumber;
        this.topic         = topic;
        this.payload       = payload;
        this.status        = status;
        this.createdAt     = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()               { return id; }
    public String getEventId()        { return eventId; }
    public String getAccountNumber()  { return accountNumber; }
    public String getTopic()          { return topic; }
    public String getPayload()        { return payload; }
    public OutboxStatus getStatus()   { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setEventId(String eventId)           { this.eventId = eventId; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public void setTopic(String topic)               { this.topic = topic; }
    public void setPayload(String payload)           { this.payload = payload; }
    public void setStatus(OutboxStatus status)       { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
package com.citicore.transaction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    /**
     * txnRef — used as Kafka partition key.
     * Guarantees all saga events for the same transaction land on the same partition
     * and are consumed in order.
     */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "topic", nullable = false)
    private String topic;

    /**
     * Raw JSON string of the event payload.
     * OutboxPublisher sends this via KafkaTemplate<String,String> with StringSerializer
     * to avoid double-encoding (JsonSerializer would wrap it in extra quotes).
     */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OutboxEvent() {}

    public OutboxEvent(String eventId, String aggregateId, String topic,
                       String payload, OutboxStatus status, LocalDateTime createdAt) {
        this.eventId     = eventId;
        this.aggregateId = aggregateId;
        this.topic       = topic;
        this.payload     = payload;
        this.status      = status;
        this.createdAt   = createdAt;
    }

    public Long getId()               { return id; }
    public String getEventId()        { return eventId; }
    public String getAggregateId()    { return aggregateId; }
    public String getTopic()          { return topic; }
    public String getPayload()        { return payload; }
    public OutboxStatus getStatus()   { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(OutboxStatus s) { this.status = s; }
}
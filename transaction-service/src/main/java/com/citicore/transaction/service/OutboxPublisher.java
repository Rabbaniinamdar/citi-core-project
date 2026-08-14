package com.citicore.transaction.service;

import com.citicore.transaction.entity.OutboxEvent;
import com.citicore.transaction.entity.OutboxStatus;
import com.citicore.transaction.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox Publisher — the relay from DB to Kafka.
 *
 * Polls outbox_events table every 5 seconds for PENDING records.
 * Sends each payload to its target Kafka topic via StringSerializer.
 * Marks as SENT on success, FAILED on error (retried next poll cycle).
 *
 * KEY DETAIL: Uses KafkaTemplate<String, String> with StringSerializer.
 * The payload is already a JSON string in the DB.
 * Using JsonSerializer (KafkaTemplate<String, Object>) would double-encode:
 *   {"txnRef":...}  →  "{\"txnRef\":...}"   ← WRONG, breaks consumer deserialization
 * StringSerializer sends bytes as-is:
 *   {"txnRef":...}  →  {"txnRef":...}        ← CORRECT ✅
 */
@Service
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate) {
        this.outboxRepository    = outboxRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findByStatus(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                stringKafkaTemplate.send(
                        event.getTopic(),        // e.g. "transfer-requested-topic"
                        event.getAggregateId(),  // txnRef → Kafka partition key
                        event.getPayload()       // raw JSON string → no re-serialization
                );
                event.setStatus(OutboxStatus.SENT);
                System.out.println("📤 [OUTBOX SENT] topic=" + event.getTopic()
                        + " | key=" + event.getAggregateId());

            } catch (Exception ex) {
                event.setStatus(OutboxStatus.FAILED);
                System.out.println("❌ [OUTBOX FAILED] topic=" + event.getTopic()
                        + " | key=" + event.getAggregateId()
                        + " | error=" + ex.getMessage());
                // Status stays FAILED — next poll will retry (Kafka was likely down)
            }
            outboxRepository.save(event);
        }
    }
}
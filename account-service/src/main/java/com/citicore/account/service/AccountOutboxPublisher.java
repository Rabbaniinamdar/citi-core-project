package com.citicore.account.service;

import com.citicore.account.entity.AccountOutboxEvent;
import com.citicore.account.entity.OutboxStatus;
import com.citicore.account.repository.AccountOutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Account Outbox Publisher — relay from DB to Kafka.
 *
 
 * Polls account_outbox table every 5 seconds for PENDING records.
 * Sends each payload to its target Kafka topic.
 * Marks SENT on success, FAILED on error (retried on next poll).
 *
 * CRITICAL: Uses KafkaTemplate<String, String> with StringSerializer.
 *
 * Why not KafkaTemplate<String, Object>?
 *   The payload stored in account_outbox is already a JSON string.
 *   JsonSerializer would see a String and wrap it in extra quotes:
 *     DB:    {"txnRef":"CITI-001",...}
 *     Wire:  "{\"txnRef\":\"CITI-001\",...}"   ← double encoded ❌
 *
 *   StringSerializer converts String → bytes as-is:
 *     DB:    {"txnRef":"CITI-001",...}
 *     Wire:  {"txnRef":"CITI-001",...}          ← clean JSON ✅
 *
 *   Consumers (DebitConsumer, CreditConsumer, etc.) receive the clean string
 *   and call objectMapper.readValue(payload, TargetClass.class) manually.
 */
@Service
public class AccountOutboxPublisher {

    private final AccountOutboxRepository repository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public AccountOutboxPublisher(
            AccountOutboxRepository repository,
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate) {
        this.repository           = repository;
        this.stringKafkaTemplate  = stringKafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publish() {
        List<AccountOutboxEvent> events = repository.findByStatus(OutboxStatus.PENDING);

        for (AccountOutboxEvent event : events) {
            try {
                stringKafkaTemplate.send(
                        event.getTopic(),          // e.g. "debit-success-topic"
                        event.getAccountNumber(),  // partition key
                        event.getPayload()         // raw JSON string — no re-serialization
                );
                event.setStatus(OutboxStatus.SENT);
                System.out.println("📤 [ACC OUTBOX SENT] topic=" + event.getTopic()
                        + " | key=" + event.getAccountNumber());

            } catch (Exception e) {
                event.setStatus(OutboxStatus.FAILED);
                System.out.println("❌ [ACC OUTBOX FAILED] topic=" + event.getTopic()
                        + " | error=" + e.getMessage());
                // Kafka was likely down — stays FAILED, retried on next poll cycle
            }
            repository.save(event);
        }
    }
}
package com.citicore.account.service;

import com.citicore.account.entity.AccountOutboxEvent;
import com.citicore.account.entity.OutboxStatus;
import com.citicore.account.repository.AccountOutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
            @Qualifier("stringKafkaTemplate")
            KafkaTemplate<String, String> stringKafkaTemplate) {

        this.repository = repository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publish() {

        List<AccountOutboxEvent> events =
                repository.findByStatusIn(
                        List.of(
                                OutboxStatus.PENDING,
                                OutboxStatus.FAILED
                        )
                );

        for (AccountOutboxEvent event : events) {
            try {
                stringKafkaTemplate.send(
                        event.getTopic(),
                        event.getAccountNumber(),
                        event.getPayload()
                ).get(5, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.SENT);
                repository.save(event);

                System.out.println(
                        "📤 [ACC OUTBOX SENT] topic="
                                + event.getTopic()
                                + " | key="
                                + event.getAccountNumber()
                );

            } catch (Exception e) {

                System.out.println(
                        "❌ [ACC OUTBOX FAILED] topic="
                                + event.getTopic()
                                + " | error="
                                + e.getMessage()
                );

                // Keep PENDING so the next scheduler cycle retries it
                event.setStatus(OutboxStatus.PENDING);
                repository.save(event);
            }
        }
    }
}
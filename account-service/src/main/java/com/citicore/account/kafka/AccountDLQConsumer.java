package com.citicore.account.kafka;

import com.citicore.account.entity.DeadLetterEvent;
import com.citicore.account.repository.DeadLetterEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Consumes all Dead Letter Topic (DLT) messages for the account service.
 *
 * Messages land here after exhausting all retry attempts:
 *   Attempt 1 → wait 2s → Attempt 2 → wait 4s → Attempt 3 → wait 8s → DLT
 *
 * Spring Kafka's DeadLetterPublishingRecoverer auto-creates <topic>.DLT
 * and attaches error details as Kafka headers:
 *
 *   kafka_dlt-exception-message   → exception message
 *   kafka_dlt-exception-fqcn     → exception class name
 *   kafka_dlt-original-topic     → original topic
 *   kafka_dlt-original-partition → original partition
 *   kafka_dlt-original-offset    → original offset
 *   kafka_dlt-exception-stacktrace → full stack trace
 *
 * Each failed message is saved to dead_letter_events table.
 * Admin can then:
 *   - Inspect via GET  /api/v1/admin/dlq/pending
 *   - Replay via POST /api/v1/admin/dlq/replay/{id}
 *   - Ignore via POST /api/v1/admin/dlq/ignore/{id}
 */
@Service
public class AccountDLQConsumer {

    private final DeadLetterEventRepository repository;

    public AccountDLQConsumer(DeadLetterEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = {
                    "transfer-requested-topic.DLT",
                    "debit-success-topic.DLT",
                    "debit-failed-topic.DLT",
                    "credit-failed-topic.DLT",
                    "reversal-topic.DLT"
            },
            groupId = "account-dlq-group"
    )
    public void handleDLQ(
            ConsumerRecord<String, String> record,
            @Header(name = "kafka_dlt-exception-message",
                    required = false) byte[] exceptionMessageBytes,
            @Header(name = "kafka_dlt-exception-fqcn",
                    required = false) byte[] exceptionClassBytes
    ) {
        String errorMessage = exceptionMessageBytes != null
                ? new String(exceptionMessageBytes, StandardCharsets.UTF_8)
                : "Unknown error";

        String exceptionClass = exceptionClassBytes != null
                ? new String(exceptionClassBytes, StandardCharsets.UTF_8)
                : "Unknown";

        System.out.println("💀 [ACCOUNT DLQ]"
                + " | topic="     + record.topic()
                + " | partition=" + record.partition()
                + " | offset="    + record.offset()
                + " | key="       + record.key()
                + " | exception=" + exceptionClass
                + " | error="     + errorMessage);

        DeadLetterEvent dlqEvent = new DeadLetterEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                errorMessage,
                exceptionClass
        );

        repository.save(dlqEvent);
        System.out.println("💾 [ACCOUNT DLQ] Saved to dead_letter_events");
    }
}
package com.citicore.transaction.kafka;

import com.citicore.transaction.entity.DeadLetterEvent;
import com.citicore.transaction.repository.DeadLetterEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Consumes all Dead Letter Topic (DLT) messages for the transaction service.
 *
 * Spring Kafka's DeadLetterPublishingRecoverer automatically routes messages
 * to <topic>.DLT after all retry attempts are exhausted (3x exponential backoff).
 *
 * Each failed message is persisted to dead_letter_events table so admins can:
 *   - Inspect the payload and error
 *   - Replay via POST /api/v1/admin/dlq/replay/{id}
 *   - Ignore via POST /api/v1/admin/dlq/ignore/{id}
 *
 * Spring Kafka DLT headers added automatically:
 *   kafka_dlt-exception-message  → exception message text
 *   kafka_dlt-exception-fqcn    → fully qualified exception class
 *   kafka_dlt-original-topic    → original topic name
 *   kafka_dlt-original-offset   → original offset
 */
@Service
public class TransactionDLQConsumer {

    private final DeadLetterEventRepository repository;

    public TransactionDLQConsumer(DeadLetterEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = {
                    "credit-success-topic.DLT",
                    "credit-failed-topic.DLT",
                    "debit-failed-topic.DLT",
                    "reversal-success-topic.DLT"
            },
            groupId = "transaction-dlq-group"
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

        System.out.println("💀 [TXN DLQ]"
                + " | topic="     + record.topic()
                + " | partition=" + record.partition()
                + " | offset="    + record.offset()
                + " | key="       + record.key()
                + " | exception=" + exceptionClass
                + " | error="     + errorMessage);

        // Persist to DB — admin can inspect and replay or ignore
        DeadLetterEvent dlqEvent = new DeadLetterEvent(
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                errorMessage,
                exceptionClass
        );

        repository.save(dlqEvent);
        System.out.println("💾 [TXN DLQ] Saved to dead_letter_events id="
                + dlqEvent.getId());
    }
}
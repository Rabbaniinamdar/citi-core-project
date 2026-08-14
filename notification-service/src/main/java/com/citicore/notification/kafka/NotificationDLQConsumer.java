package com.citicore.notification.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class NotificationDLQConsumer {

    /**
     * Consumes all Dead Letter Topic (DLT) messages for the notification service.
     *
     * Spring Kafka's DeadLetterPublishingRecoverer auto-creates <topic>.DLT
     * and routes messages there after all retry attempts are exhausted.
     *
     * Headers added by Spring Kafka to DLT messages:
     *   kafka_dlt-exception-message   → exception message text
     *   kafka_dlt-exception-fqcn      → fully qualified exception class name
     *   kafka_dlt-original-topic      → original topic name
     *   kafka_dlt-original-partition  → original partition
     *   kafka_dlt-original-offset     → original offset
     *
     * NOTE: Notification service is stateless — we log DLQ events.
     * In production, connect this to PagerDuty / Slack / OpsGenie for alerting.
     */
    @KafkaListener(
            topics = {
                    "account-events-topic.DLT",
                    "credit-success-topic.DLT",
                    "credit-failed-topic.DLT",
                    "reversal-success-topic.DLT",
                    "otp-topic.DLT",
                    "kyc-topic.DLT"
            },
            groupId = "notification-dlq-group"
    )
    public void handleDLQ(
            ConsumerRecord<String, String> record,
            @Header(name = "kafka_dlt-exception-message",
                    required = false) byte[] exceptionMessageBytes,
            @Header(name = "kafka_dlt-exception-fqcn",
                    required = false) byte[] exceptionClassBytes,
            @Header(name = "kafka_dlt-original-topic",
                    required = false) byte[] originalTopicBytes
    ) {
        String errorMessage = exceptionMessageBytes != null
                ? new String(exceptionMessageBytes, StandardCharsets.UTF_8)
                : "Unknown error";

        String exceptionClass = exceptionClassBytes != null
                ? new String(exceptionClassBytes, StandardCharsets.UTF_8)
                : "Unknown";

        String originalTopic = originalTopicBytes != null
                ? new String(originalTopicBytes, StandardCharsets.UTF_8)
                : record.topic().replace(".DLT", "");

        // 🚨 In production: alert via PagerDuty / Slack / OpsGenie
        System.out.println("💀 [NOTIFICATION DLQ]"
                + " | originalTopic=" + originalTopic
                + " | DLTopic="      + record.topic()
                + " | partition="    + record.partition()
                + " | offset="       + record.offset()
                + " | key="          + record.key()
                + " | exception="    + exceptionClass
                + " | error="        + errorMessage
                + " | payload="      + record.value()
        );

        // Notification service does not persist DLQ to DB (stateless service).
        // If you need DB persistence, add a DeadLetterEvent entity + repository
        // same as account-service and transaction-service implementations.
    }
}
package com.citicore.notification.kafka;

import com.citicore.events.account.AccountEvent;
import com.citicore.events.account.AccountEventType;
import com.citicore.notification.service.EmailService;
import com.citicore.notification.template.AccountEmailTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class AccountEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public AccountEventConsumer(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Consumes AccountEvent from account-events-topic.
     *
     * Published by AccountOutboxPublisher (account-service) on:
     *   - ACCOUNT_CREATED  → welcome email with account number + opening deposit
     *   - ACCOUNT_DEBITED  → debit alert email
     *   - ACCOUNT_CREDITED → credit alert email
     *
     * Payload is a raw JSON string (outbox pattern) — parsed manually.
     * Offset committed only after email is sent successfully (MANUAL ack mode).
     */
    @KafkaListener(
            topics = "account-events-topic",
            groupId = "notification-group"
    )
    public void consume(String payload, Acknowledgment ack) {

        AccountEvent event;
        try {
            // ✅ Parse raw JSON string from outbox — NOT a POJO from JsonDeserializer
            event = objectMapper.readValue(payload, AccountEvent.class);
        } catch (Exception e) {
            // Poison pill — malformed JSON will never parse correctly, skip it
            System.out.println("❌ [ACCOUNT EVENT] Failed to parse payload: " + e.getMessage());
            ack.acknowledge(); // ack so we don't block the partition on bad data
            return;
        }

        System.out.println("📥 [ACCOUNT EVENT] eventType=" + event.getEventType()
                + " | acc=" + event.getAccountNumber());

        try {
            if (event.getEventType() == AccountEventType.ACCOUNT_CREATED) {

                emailService.sendHtml(
                        event.getEmail(),
                        "Welcome to CitiCore — Account Created Successfully",
                        AccountEmailTemplate.accountCreatedTemplate(
                                event.getEmail(),
                                event.getAccountNumber(),
                                event.getAmount()
                        )
                );

            } else if (event.getEventType() == AccountEventType.ACCOUNT_DEBITED) {

                emailService.sendHtml(
                        event.getEmail(),
                        "CitiCore Alert — Debit of ₹" + event.getAmount(),
                        AccountEmailTemplate.debitTemplate(
                                event.getEmail(),
                                event.getAmount(),
                                event.getTxnRef(),
                                event.getAccountNumber()
                        )
                );

            } else if (event.getEventType() == AccountEventType.ACCOUNT_CREDITED) {

                emailService.sendHtml(
                        event.getEmail(),
                        "CitiCore Alert — Credit of ₹" + event.getAmount(),
                        AccountEmailTemplate.creditTemplate(
                                event.getEmail(),
                                event.getAmount(),
                                event.getTxnRef(),
                                event.getAccountNumber()
                        )
                );

            } else {
                System.out.println("⚠️ [ACCOUNT EVENT] Unknown eventType: " + event.getEventType());
            }

            // ✅ Commit offset only after successful email send
            ack.acknowledge();

        } catch (Exception e) {
            System.out.println("❌ [ACCOUNT EVENT] Email send failed: " + e.getMessage());
            // Do NOT call ack.acknowledge()
            // Spring Kafka will redeliver and retry (with exponential backoff)
            // After max retries, routes to account-events-topic.DLT
        }
    }
}
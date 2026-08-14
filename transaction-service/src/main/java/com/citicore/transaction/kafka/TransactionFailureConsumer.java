package com.citicore.transaction.kafka;

import com.citicore.events.transaction.CreditFailedEvent;
import com.citicore.events.transaction.DebitFailedEvent;
import com.citicore.transaction.entity.OutboxEvent;
import com.citicore.transaction.entity.OutboxStatus;
import com.citicore.transaction.entity.TransactionStatus;
import com.citicore.transaction.repository.OutboxRepository;
import com.citicore.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionFailureConsumer {

    private final TransactionRepository repository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionFailureConsumer(TransactionRepository repository,
                                      OutboxRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.repository      = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper    = objectMapper;
    }

    /**
     * Handles credit-failed-topic — credit step of saga failed.
     *
     * Published by: AccountOutboxPublisher (account-service) when CreditConsumer
     *               throws an exception (e.g. receiver account not found).
     *
     * Action:
     *   1. Update transaction status → FAILED
     *   2. Save OutboxEvent → reversal-topic (triggers ReversalConsumer to refund sender)
     *
     * WHY outbox for reversal trigger?
     *   Direct kafkaTemplate.send() here has dual-write risk.
     *   Saving to outbox guarantees the reversal event is eventually published.
     *
     * Idempotency: skip if already FAILED to prevent duplicate reversals.
     */
    @KafkaListener(
            topics = "credit-failed-topic",
            groupId = "transaction-credit-failure-group"
    )
    public void onCreditFailure(String payload) {

        CreditFailedEvent event;
        try {
            event = objectMapper.readValue(payload, CreditFailedEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [CREDIT FAILED] Parse error: " + e.getMessage());
            return;
        }

        System.out.println("📥 [CREDIT FAILED] txnRef=" + event.getTxnRef()
                + " | reason=" + event.getReason());

        repository.findByTxnRef(event.getTxnRef())
                .ifPresentOrElse(txn -> {

                    // ✅ Idempotency — skip if already marked FAILED
                    if (txn.getStatus() == TransactionStatus.FAILED) {
                        System.out.println("⚠️ [CREDIT FAILED] Already FAILED — skipping");
                        return;
                    }

                    txn.setStatus(TransactionStatus.FAILED);
                    repository.save(txn);

                    // Trigger reversal via outbox (at-least-once guarantee)
                    saveToOutbox(event, event.getTxnRef(), "reversal-topic");
                    System.out.println("📤 [REVERSAL QUEUED] txnRef=" + event.getTxnRef());

                }, () -> System.out.println("⚠️ [CREDIT FAILED] Txn not found: " + event.getTxnRef()));
    }

    /**
     * Handles debit-failed-topic — debit step of saga failed.
     *
     * Published by: AccountOutboxPublisher (account-service) when DebitConsumer
     *               throws (e.g. insufficient balance, min balance violation).
     *
     * Action: Update transaction status → FAILED.
     *
     * NO reversal needed — money was never debited.
     */
    @KafkaListener(
            topics = "debit-failed-topic",
            groupId = "transaction-debit-failure-group"
    )
    public void handleDebitFailure(String payload) {

        DebitFailedEvent event;
        try {
            event = objectMapper.readValue(payload, DebitFailedEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [DEBIT FAILED] Parse error: " + e.getMessage());
            return;
        }

        System.out.println("📥 [DEBIT FAILED] txnRef=" + event.getTxnRef()
                + " | reason=" + event.getReason());

        repository.findByTxnRef(event.getTxnRef())
                .ifPresentOrElse(txn -> {

                    if (txn.getStatus() == TransactionStatus.FAILED) {
                        System.out.println("⚠️ [DEBIT FAILED] Already FAILED — skipping");
                        return;
                    }

                    txn.setStatus(TransactionStatus.FAILED);
                    repository.save(txn);
                    System.out.println("❌ [TXN FAILED] debit failed | txnRef=" + event.getTxnRef());

                }, () -> System.out.println("⚠️ [DEBIT FAILED] Txn not found: " + event.getTxnRef()));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private void saveToOutbox(Object event, String aggregateId, String topic) {
        try {
            String json = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEvent(
                    UUID.randomUUID().toString(),
                    aggregateId,
                    topic,
                    json,
                    OutboxStatus.PENDING,
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save outbox event for reversal", e);
        }
    }
}
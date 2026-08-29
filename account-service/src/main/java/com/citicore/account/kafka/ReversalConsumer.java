package com.citicore.account.kafka;

import com.citicore.account.entity.AccountOutboxEvent;
import com.citicore.account.entity.OutboxStatus;
import com.citicore.account.repository.AccountOutboxRepository;
import com.citicore.account.service.AccountService;
import com.citicore.events.transaction.CreditFailedEvent;
import com.citicore.events.transaction.ReversalSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga Compensating Transaction — Refund the sender's account.
 *
 * Triggered by: reversal-topic
 * Published by: OutboxPublisher (transaction-service) when credit-failed-topic
 *               is received and txn is marked FAILED.
 *
 * What it does:
 *   Credits back the fromAccount (the sender) — undoing the debit that
 *   succeeded in saga step 1, because saga step 2 (credit) failed.
 *
 * Idempotency:
 *   Uses txnRef + "_REVERSAL" suffix.
 *   AccountService.credit() checks statementRepository.existsByTxnRef()
 *   before processing — safe on Kafka redelivery.
 *
 * On SUCCESS:
 *   → saves ReversalSuccessEvent to outbox
 *   → AccountOutboxPublisher publishes to reversal-success-topic
 *   → TransactionEventConsumer (txn-service) marks txn REVERSED 🔄
 *   → NotificationService sends refund email
 *
 * On FAILURE:
 *   → logs the error (manual intervention needed — money is in limbo)
 *   → in production: alert via PagerDuty / OpsGenie
 */
@Service
public class ReversalConsumer {

    private final AccountService accountService;
    private final AccountOutboxRepository accountOutboxRepository;
    private final ObjectMapper objectMapper;

    public ReversalConsumer(AccountService accountService,
                            AccountOutboxRepository accountOutboxRepository,
                            ObjectMapper objectMapper) {
        this.accountService          = accountService;
        this.accountOutboxRepository = accountOutboxRepository;
        this.objectMapper            = objectMapper;
    }

    @KafkaListener(
            topics = "reversal-topic",
            groupId = "account-reversal-group"
    )
    public void reverse(String payload) {

        CreditFailedEvent event;
        try {
            event = objectMapper.readValue(payload, CreditFailedEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [REVERSAL] Poison pill — cannot parse payload: "
                    + e.getMessage());
            return;
        }

        System.out.println("🔄 [REVERSAL START] txnRef=" + event.getTxnRef()
                + " | refunding acc=" + event.getFromAccount()
                + " | amount=" + event.getAmount());

        try {
            // Credit back the SENDER (fromAccount) — compensating transaction
            accountService.credit(
                    event.getFromAccount(),
                    event.getAmount(),
                    event.getTxnRef() + "_REVERSAL",  // unique suffix — idempotent
                    event.getAuthUserId(),
                    ""
            );

            System.out.println("✅ [REVERSAL SUCCESS] txnRef=" + event.getTxnRef());

            // ── Success → publish reversal-success-topic via outbox ───────────
            ReversalSuccessEvent successEvent = new ReversalSuccessEvent(
                    event.getTxnRef(),
                    event.getFromAccount(),
                    event.getAmount(),
                    event.getAuthUserId()
            );
            saveToOutbox(successEvent, event.getTxnRef(), "reversal-success-topic");
            System.out.println("📤 [OUTBOX SAVED] reversal-success-topic txnRef="
                    + event.getTxnRef());

        } catch (Exception ex) {
            // 🚨 CRITICAL — reversal failed. Money is in limbo:
            //   - Debit from sender succeeded
            //   - Credit to receiver failed
            //   - Reversal (refund to sender) also failed
            // Manual intervention required. Alert ops team.
            System.out.println("🚨 [REVERSAL FAILED] txnRef=" + event.getTxnRef()
                    + " | MANUAL INTERVENTION REQUIRED | error=" + ex.getMessage());

            // Rethrow so Spring Kafka retries with exponential backoff,
            // then routes to reversal-topic.DLT for admin replay
            throw new RuntimeException("Reversal failed for txnRef=" + event.getTxnRef(), ex);
        }
    }

    private void saveToOutbox(Object event, String aggregateId, String topic) {
        try {
            String json = objectMapper.writeValueAsString(event);

            AccountOutboxEvent outbox = new AccountOutboxEvent();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAccountNumber(aggregateId);
            outbox.setTopic(topic);
            outbox.setPayload(json);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setCreatedAt(LocalDateTime.now());

            accountOutboxRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save reversal outbox event", e);
        }
    }
}
package com.citicore.account.kafka;

import com.citicore.account.entity.AccountOutboxEvent;
import com.citicore.account.entity.OutboxStatus;
import com.citicore.account.repository.AccountOutboxRepository;
import com.citicore.account.service.AccountService;
import com.citicore.events.transaction.DebitFailedEvent;
import com.citicore.events.transaction.DebitSuccessEvent;
import com.citicore.events.transaction.TransactionRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga Step 1 — Debit the sender's account.
 *
 * Triggered by: transfer-requested-topic
 * Published by: OutboxPublisher (transaction-service) after transfer is initiated
 *
 * On SUCCESS:
 *   → saves DebitSuccessEvent to account_outbox
 *   → AccountOutboxPublisher publishes to debit-success-topic
 *   → CreditConsumer picks it up (saga step 2)
 *
 * On FAILURE (InsufficientBalance, MinimumBalance, Unauthorized, etc.):
 *   → saves DebitFailedEvent to account_outbox
 *   → AccountOutboxPublisher publishes to debit-failed-topic
 *   → TransactionFailureConsumer marks txn as FAILED (no reversal needed)
 *
 * KEY: Accepts String payload (not POJO) — outbox publisher uses StringSerializer.
 * Parse manually with objectMapper.readValue().
 *
 * KEY: groupId = "account-debit-group" — must be stable.
 * Missing/random groupId → full replay on every restart → duplicate debits 💀
 */
@Service
public class DebitConsumer {

    private final AccountService accountService;
    private final AccountOutboxRepository accountOutboxRepository;
    private final ObjectMapper objectMapper;

    public DebitConsumer(AccountService accountService,
                         AccountOutboxRepository accountOutboxRepository,
                         ObjectMapper objectMapper) {
        this.accountService          = accountService;
        this.accountOutboxRepository = accountOutboxRepository;
        this.objectMapper            = objectMapper;
    }

    @KafkaListener(
            topics = "transfer-requested-topic",
            groupId = "account-debit-group"
    )
    public void onTransfer(String payload) {

        // ── Parse raw JSON string from outbox ─────────────────────────────────
        TransactionRequestedEvent event;
        try {
            event = objectMapper.readValue(payload, TransactionRequestedEvent.class);
        } catch (Exception e) {
            // Poison pill — malformed JSON will never parse correctly
            // Don't rethrow: retrying is pointless, just log and skip
            System.out.println("❌ [DEBIT] Poison pill — cannot parse payload: "
                    + e.getMessage());
            return;
        }

        System.out.println("📥 [DEBIT] txnRef=" + event.getTxnRef()
                + " | acc=" + event.getFromAccount()
                + " | amount=" + event.getAmount());

        try {
            accountService.debit(
                    event.getFromAccount(),
                    event.getAmount(),
                    event.getTxnRef() + "_DEBIT",   // unique txnRef for idempotency
                    event.getAuthUserId(),
                    ""
            );

            System.out.println("✅ [DEBIT SUCCESS] txnRef=" + event.getTxnRef());

            // ── Success → save DebitSuccessEvent to outbox ────────────────────
            DebitSuccessEvent successEvent = new DebitSuccessEvent(
                    event.getTxnRef(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount(),
                    event.getAuthUserId()
            );
            saveToOutbox(successEvent, event.getTxnRef(), "debit-success-topic");
            System.out.println("📤 [OUTBOX SAVED] debit-success-topic txnRef="
                    + event.getTxnRef());

        } catch (Exception ex) {
            System.out.println("❌ [DEBIT FAILED] txnRef=" + event.getTxnRef()
                    + " | error=" + ex.getMessage());

            // ── Failure → save DebitFailedEvent to outbox ─────────────────────
            DebitFailedEvent failEvent = new DebitFailedEvent(
                    event.getTxnRef(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount(),
                    ex.getMessage(),
                    event.getAuthUserId()
            );
            saveToOutbox(failEvent, event.getTxnRef(), "debit-failed-topic");
            System.out.println("📤 [OUTBOX SAVED] debit-failed-topic txnRef="
                    + event.getTxnRef());

            // Do NOT rethrow — business failures (InsufficientBalance) should
            // not be retried. We've already routed to debit-failed-topic.
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
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}
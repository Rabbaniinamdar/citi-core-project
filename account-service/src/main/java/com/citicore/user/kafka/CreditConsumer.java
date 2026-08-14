package com.citicore.user.kafka;

import com.citicore.user.entity.AccountOutboxEvent;
import com.citicore.user.entity.OutboxStatus;
import com.citicore.user.repository.AccountOutboxRepository;
import com.citicore.user.service.AccountService;
import com.citicore.events.transaction.CreditFailedEvent;
import com.citicore.events.transaction.CreditSuccessEvent;
import com.citicore.events.transaction.DebitSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga Step 2 — Credit the receiver's account.
 *
 * Triggered by: debit-success-topic
 * Published by: AccountOutboxPublisher after DebitConsumer succeeds
 *
 * On SUCCESS:
 *   → saves CreditSuccessEvent to account_outbox
 *   → AccountOutboxPublisher publishes to credit-success-topic
 *   → TransactionEventConsumer (txn-service) marks txn CREDIT_SUCCESS ✅
 *   → NotificationService sends success email
 *
 * On FAILURE (account not found, DB error, etc.):
 *   → saves CreditFailedEvent to account_outbox
 *   → AccountOutboxPublisher publishes to credit-failed-topic
 *   → TransactionFailureConsumer (txn-service) marks txn FAILED
 *     and triggers reversal via reversal-topic
 *   → ReversalConsumer credits money back to sender
 *   → NotificationService sends failure + reversal emails
 */
@Service
public class CreditConsumer {

    private final AccountService accountService;
    private final AccountOutboxRepository accountOutboxRepository;
    private final ObjectMapper objectMapper;

    public CreditConsumer(AccountService accountService,
                          AccountOutboxRepository accountOutboxRepository,
                          ObjectMapper objectMapper) {
        this.accountService          = accountService;
        this.accountOutboxRepository = accountOutboxRepository;
        this.objectMapper            = objectMapper;
    }

    @KafkaListener(
            topics = "debit-success-topic",
            groupId = "account-credit-group"
    )
    public void onDebitSuccess(String payload) {

        DebitSuccessEvent event;
        try {
            event = objectMapper.readValue(payload, DebitSuccessEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [CREDIT] Poison pill — cannot parse payload: "
                    + e.getMessage());
            return;
        }

        System.out.println("📥 [CREDIT] txnRef=" + event.getTxnRef()
                + " | acc=" + event.getToAccount()
                + " | amount=" + event.getAmount());

        try {
            accountService.credit(
                    event.getToAccount(),
                    event.getAmount(),
                    event.getTxnRef() + "_CREDIT",  // unique txnRef for idempotency
                    event.getAuthUserId(),
                    ""
            );

            System.out.println("✅ [CREDIT SUCCESS] txnRef=" + event.getTxnRef());

            // ── Success → save CreditSuccessEvent to outbox ───────────────────
            CreditSuccessEvent successEvent = new CreditSuccessEvent(
                    event.getTxnRef(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount(),
                    event.getAuthUserId()
            );
            saveToOutbox(successEvent, event.getTxnRef(), "credit-success-topic");
            System.out.println("📤 [OUTBOX SAVED] credit-success-topic txnRef="
                    + event.getTxnRef());

        } catch (Exception ex) {
            System.out.println("❌ [CREDIT FAILED] txnRef=" + event.getTxnRef()
                    + " | error=" + ex.getMessage());

            // ── Failure → save CreditFailedEvent to outbox ────────────────────
            // TransactionFailureConsumer will trigger reversal (refund sender)
            CreditFailedEvent failEvent = new CreditFailedEvent(
                    event.getTxnRef(),
                    event.getFromAccount(),
                    event.getToAccount(),
                    event.getAmount(),
                    ex.getMessage(),
                    event.getAuthUserId()
            );
            saveToOutbox(failEvent, event.getTxnRef(), "credit-failed-topic");
            System.out.println("📤 [OUTBOX SAVED] credit-failed-topic txnRef="
                    + event.getTxnRef());
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
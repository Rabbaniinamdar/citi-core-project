package com.citicore.transaction.kafka;

import com.citicore.events.transaction.CreditSuccessEvent;
import com.citicore.events.transaction.ReversalSuccessEvent;
import com.citicore.transaction.entity.TransactionStatus;
import com.citicore.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionEventConsumer {

    private final TransactionRepository repository;
    private final ObjectMapper objectMapper;

    public TransactionEventConsumer(TransactionRepository repository,
                                    ObjectMapper objectMapper) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles credit-success-topic — full saga completed successfully.
     *
     * Published by: AccountOutboxPublisher (account-service) after CreditConsumer
     *               successfully credits the receiver's account.
     *
     * Action: Update transaction status → CREDIT_SUCCESS.
     *
     * Idempotency guard: if status is already CREDIT_SUCCESS, skip.
     * This prevents double-update on Kafka redelivery (at-least-once).
     *
     * Group ID: "transaction-status-group"
     * MUST be stable — random group ID causes full replay on every restart.
     */
    @KafkaListener(
            topics = "credit-success-topic",
            groupId = "transaction-status-group"
    )
    public void handleCreditSuccess(String payload) {

        CreditSuccessEvent event;
        try {
            event = objectMapper.readValue(payload, CreditSuccessEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [TXN SUCCESS] Parse failed: " + e.getMessage());
            return; // Poison pill — bad JSON, skip (don't rethrow, no retry benefit)
        }

        System.out.println("📥 [TXN SUCCESS] txnRef=" + event.getTxnRef());

        repository.findByTxnRef(event.getTxnRef())
                .ifPresentOrElse(txn -> {

                    // ✅ Idempotency guard
                    if (txn.getStatus() == TransactionStatus.CREDIT_SUCCESS) {
                        System.out.println("⚠️ [TXN SUCCESS] Already CREDIT_SUCCESS — skipping");
                        return;
                    }

                    txn.setStatus(TransactionStatus.CREDIT_SUCCESS);
                    repository.save(txn);
                    System.out.println("✅ [TXN COMPLETE] txnRef=" + event.getTxnRef()
                            + " → CREDIT_SUCCESS");

                }, () -> System.out.println("⚠️ [TXN SUCCESS] Txn not found: " + event.getTxnRef()));
    }

    /**
     * Handles reversal-success-topic — compensating transaction completed.
     *
     * Published by: ReversalConsumer (account-service) after crediting money
     *               back to the sender's account.
     *
     * Action: Update transaction status → REVERSED.
     *
     * This completes the audit trail for failed+reversed transactions.
     *
     * Group ID: "transaction-reversal-group"
     */
    @KafkaListener(
            topics = "reversal-success-topic",
            groupId = "transaction-reversal-group"
    )
    public void handleReversalSuccess(String payload) {

        ReversalSuccessEvent event;
        try {
            event = objectMapper.readValue(payload, ReversalSuccessEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [REVERSAL] Parse failed: " + e.getMessage());
            return;
        }

        System.out.println("📥 [REVERSAL SUCCESS] txnRef=" + event.getTxnRef());

        repository.findByTxnRef(event.getTxnRef())
                .ifPresentOrElse(txn -> {

                    if (txn.getStatus() == TransactionStatus.REVERSED) {
                        System.out.println("⚠️ [REVERSAL] Already REVERSED — skipping");
                        return;
                    }

                    txn.setStatus(TransactionStatus.REVERSED);
                    repository.save(txn);
                    System.out.println("🔄 [TXN REVERSED] txnRef=" + event.getTxnRef());

                }, () -> System.out.println("⚠️ [REVERSAL] Txn not found: " + event.getTxnRef()));
    }
}
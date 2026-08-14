package com.citicore.notification.kafka;

import com.citicore.events.transaction.CreditFailedEvent;
import com.citicore.events.transaction.CreditSuccessEvent;
import com.citicore.events.transaction.ReversalSuccessEvent;
import com.citicore.notification.service.EmailService;
import com.citicore.notification.template.TransactionEmailTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class TransactionEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public TransactionEventConsumer(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Transfer SUCCESS — full saga completed.
     * Triggered by: CreditConsumer (account-service) after successful credit.
     * Action: Send "Transfer Successful" email to sender.
     */
    @KafkaListener(
            topics = "credit-success-topic",
            groupId = "notification-group"
    )
    public void handleTransactionSuccess(String payload, Acknowledgment ack) {

        CreditSuccessEvent event;
        try {
            event = objectMapper.readValue(payload, CreditSuccessEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [TXN SUCCESS] Parse failed: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        System.out.println("📥 [TXN SUCCESS] txnRef=" + event.getTxnRef()
                + " | amount=" + event.getAmount());

        try {
            emailService.sendHtml(
                    resolveEmail(event.getAuthUserId()),
                    "CitiCore — Transfer Successful ✅",
                    TransactionEmailTemplate.successTemplate(
                            "Customer",           // TODO: fetch name from user-service via Feign
                            event.getAmount(),
                            event.getTxnRef(),
                            event.getToAccount()
                    )
            );

            ack.acknowledge();

        } catch (Exception e) {
            System.out.println("❌ [TXN SUCCESS] Email failed: " + e.getMessage());
            // No ack → retry → eventually DLT if all retries exhausted
        }
    }

    /**
     * Transfer FAILED — saga failed (credit failed after debit succeeded or debit failed).
     * Triggered by: CreditConsumer (account-service) when credit throws.
     * Action: Send "Transfer Failed" email to sender. Reversal is handled separately.
     */
    @KafkaListener(
            topics = "credit-failed-topic",
            groupId = "notification-group"
    )
    public void handleTransactionFailed(String payload, Acknowledgment ack) {

        CreditFailedEvent event;
        try {
            event = objectMapper.readValue(payload, CreditFailedEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [TXN FAILED] Parse failed: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        System.out.println("📥 [TXN FAILED] txnRef=" + event.getTxnRef()
                + " | reason=" + event.getReason());

        try {
            emailService.sendHtml(
                    resolveEmail(event.getAuthUserId()),
                    "CitiCore — Transfer Failed ❌",
                    TransactionEmailTemplate.failedTemplate(
                            "Customer",
                            event.getAmount(),
                            event.getTxnRef(),
                            event.getReason() != null ? event.getReason() : "Transaction could not be processed"
                    )
            );

            ack.acknowledge();

        } catch (Exception e) {
            System.out.println("❌ [TXN FAILED] Email failed: " + e.getMessage());
        }
    }

    /**
     * Transfer REVERSED — money refunded back to sender.
     * Triggered by: ReversalConsumer (account-service) after successful reversal credit.
     * Action: Send "Transfer Reversed & Refunded" email to sender.
     */
    @KafkaListener(
            topics = "reversal-success-topic",
            groupId = "notification-group"
    )
    public void handleReversalSuccess(String payload, Acknowledgment ack) {

        ReversalSuccessEvent event;
        try {
            event = objectMapper.readValue(payload, ReversalSuccessEvent.class);
        } catch (Exception e) {
            System.out.println("❌ [REVERSAL] Parse failed: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        System.out.println("📥 [REVERSAL SUCCESS] txnRef=" + event.getTxnRef()
                + " | amount=" + event.getAmount());

        try {
            emailService.sendHtml(
                    resolveEmail(event.getAuthUserId()),
                    "CitiCore — Transfer Reversed & Refunded 🔄",
                    TransactionEmailTemplate.reversalTemplate(
                            "Customer",
                            event.getAmount(),
                            event.getTxnRef()
                    )
            );

            ack.acknowledge();

        } catch (Exception e) {
            System.out.println("❌ [REVERSAL] Email failed: " + e.getMessage());
        }
    }

    /**
     * Resolves user email from authUserId.
     *
     * TODO: Replace with a Feign call to user-service:
     *   @FeignClient("user-service")
     *   String getEmailByUserId(@PathVariable Long userId);
     *
     * Placeholder returns a dummy email for development/testing.
     */
    private String resolveEmail(Long authUserId) {
        // In production: return userServiceClient.getEmailByUserId(authUserId);
        return "user-" + authUserId + "@citicore.com";
    }
}
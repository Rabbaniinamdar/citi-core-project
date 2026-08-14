package com.citicore.user.kafka;

import com.citicore.events.kyc.KycEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes KYC status change events to kyc-topic.
 *
 * Consumed by notification-service → KycEventConsumer
 * → sends KYC approved/rejected email to the user.
 *
 * NOTE: User-service publishes events DIRECTLY (no outbox pattern here).
 * KYC status updates are admin-triggered and low-frequency.
 * If Kafka is temporarily down during an admin action,
 * the admin simply re-triggers the status update.
 *
 * For saga-critical flows (debit/credit), always use the outbox.
 * For admin notifications, direct publish is acceptable.
 *
 * Uses KafkaTemplate<String, String> with StringSerializer to stay
 * consistent with the rest of the platform — consumers receive raw
 * JSON strings and parse with objectMapper.readValue().
 */
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate,
            ObjectMapper objectMapper) {
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.objectMapper        = objectMapper;
    }

    /**
     * Publishes a KYC status change event to kyc-topic.
     *
     * @param userId   authUserId — used as Kafka partition key
     * @param email    user's email — used by notification-service to send email
     * @param status   new KYC status (APPROVED / REJECTED)
     */
    public void publishKycEvent(Long userId, String email, String status) {
        try {
            KycEvent event = new KycEvent(userId, email, status);
            String payload = objectMapper.writeValueAsString(event);

            stringKafkaTemplate.send("kyc-topic", userId.toString(), payload);

            System.out.println("📤 [KYC EVENT] userId=" + userId
                    + " | status=" + status
                    + " | email=" + email);

        } catch (JsonProcessingException e) {
            // Log and continue — notification failure should not block KYC update
            System.out.println("❌ [KYC EVENT] Failed to serialize event: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("❌ [KYC EVENT] Failed to publish: " + e.getMessage());
        }
    }
}
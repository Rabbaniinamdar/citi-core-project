package com.citicore.transaction.service;

import com.citicore.transaction.entity.OutboxEvent;
import com.citicore.transaction.entity.OutboxStatus;
import com.citicore.transaction.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox Publisher
 *
 * Acts as the relay between the Transaction DB and Kafka.
 *
 * Flow:
 *
 *   Transaction DB
 *        │
 *        ▼
 *   outbox_events
 *        │
 *        │ PENDING / FAILED
 *        ▼
 *   OutboxPublisher
 *        │
 *        ▼
 *      Kafka
 *        │
 *        ▼
 *      SENT
 *
 * Important:
 *
 * The outbox payload is already a JSON String.
 * Therefore StringSerializer must be used.
 *
 * Using JsonSerializer here would double-encode the JSON.
 */
@Service
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            @Qualifier("stringKafkaTemplate")
            KafkaTemplate<String, String> stringKafkaTemplate) {

        this.outboxRepository = outboxRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    /**
     * Poll the outbox table every 5 seconds.
     *
     * We process both:
     *
     * PENDING → first attempt
     * FAILED  → retry attempt
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxRepository.findByStatusIn(
                        List.of(
                                OutboxStatus.PENDING,
                                OutboxStatus.FAILED
                        )
                );

        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {

            try {

                System.out.println(
                        "📤 [OUTBOX PUBLISHING]"
                                + " | topic=" + event.getTopic()
                                + " | key=" + event.getAggregateId()
                );

                /*
                 * IMPORTANT:
                 *
                 * KafkaTemplate.send() is asynchronous.
                 *
                 * Therefore we use .get() to wait for Kafka's
                 * acknowledgement before marking the event SENT.
                 */
                stringKafkaTemplate.send(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                ).get();

                /*
                 * Kafka successfully acknowledged the message.
                 *
                 * Only now should we mark the event as SENT.
                 */
                event.setStatus(OutboxStatus.SENT);

                outboxRepository.save(event);

                System.out.println(
                        "✅ [OUTBOX SENT]"
                                + " | topic=" + event.getTopic()
                                + " | key=" + event.getAggregateId()
                );

            } catch (Exception ex) {

                /*
                 * Kafka publish failed.
                 *
                 * Do NOT mark the event SENT.
                 *
                 * Mark it FAILED so that the next scheduler
                 * execution can retry it.
                 */
                event.setStatus(OutboxStatus.FAILED);

                outboxRepository.save(event);

                System.out.println(
                        "❌ [OUTBOX FAILED]"
                                + " | topic=" + event.getTopic()
                                + " | key=" + event.getAggregateId()
                                + " | error=" + ex.getMessage()
                );
            }
        }
    }
}
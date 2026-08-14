package com.citicore.transaction.service;

import com.citicore.transaction.entity.DeadLetterEvent;
import com.citicore.transaction.entity.DLQStatus;
import com.citicore.transaction.repository.DeadLetterEventRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin service for inspecting, replaying, and ignoring Dead Letter Queue events.
 *
 * DLQ events are created by TransactionDLQConsumer when a message exhausts
 * all retry attempts (3x exponential backoff: 2s, 4s, 8s).
 *
 * Replay: strips .DLT suffix from topic name and re-publishes original payload
 *         back to the original topic, where normal consumers will process it.
 */
@Service
public class DLQReplayService {

    private final DeadLetterEventRepository repository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public DLQReplayService(
            DeadLetterEventRepository repository,
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate) {
        this.repository           = repository;
        this.stringKafkaTemplate  = stringKafkaTemplate;
    }

    /**
     * Replays a single DLQ event back to its original Kafka topic.
     * Marks it as REPLAYED so it won't be replayed again.
     */
    public void replay(Long dlqEventId) {
        DeadLetterEvent event = repository.findById(dlqEventId)
                .orElseThrow(() -> new RuntimeException("DLQ event not found: " + dlqEventId));

        if (event.getStatus() == DLQStatus.REPLAYED) {
            throw new IllegalStateException("Event already replayed: " + dlqEventId);
        }
        if (event.getStatus() == DLQStatus.IGNORED) {
            throw new IllegalStateException("Event was marked as ignored: " + dlqEventId);
        }

        // credit-success-topic.DLT → credit-success-topic
        String originalTopic = event.getTopic().replace(".DLT", "");

        stringKafkaTemplate.send(originalTopic, event.getPayload());

        event.setStatus(DLQStatus.REPLAYED);
        event.setResolvedAt(LocalDateTime.now());
        repository.save(event);

        System.out.println("🔁 [DLQ REPLAY] id=" + dlqEventId + " → topic=" + originalTopic);
    }

    /**
     * Replays all PENDING DLQ events.
     * Useful after a downstream service outage is resolved.
     */
    public void replayAll() {
        List<DeadLetterEvent> pending = repository.findByStatus(DLQStatus.PENDING);
        System.out.println("🔁 [DLQ REPLAY ALL] count=" + pending.size());
        pending.forEach(e -> replay(e.getId()));
    }

    /**
     * Marks a DLQ event as IGNORED.
     * Use for messages that are permanently invalid (bad data, irrelevant).
     */
    public void ignore(Long dlqEventId) {
        DeadLetterEvent event = repository.findById(dlqEventId)
                .orElseThrow(() -> new RuntimeException("DLQ event not found: " + dlqEventId));
        event.setStatus(DLQStatus.IGNORED);
        event.setResolvedAt(LocalDateTime.now());
        repository.save(event);
        System.out.println("🚫 [DLQ IGNORED] id=" + dlqEventId);
    }

    public List<DeadLetterEvent> getAllPending() {
        return repository.findByStatus(DLQStatus.PENDING);
    }

    public List<DeadLetterEvent> getAll() {
        return repository.findAll();
    }
}
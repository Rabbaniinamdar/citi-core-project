package com.citicore.account.service;

import com.citicore.account.entity.DeadLetterEvent;
import com.citicore.account.entity.DLQStatus;
import com.citicore.account.repository.DeadLetterEventRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin service for Dead Letter Queue management in account-service.
 *
 * DLQ events are persisted by AccountDLQConsumer when a message fails
 * all 3 retry attempts (2s → 4s → 8s exponential backoff).
 *
 * Admin workflow:
 *   1. GET  /api/v1/admin/dlq/pending  → see all failed messages with error details
 *   2. Fix the root cause (e.g. account not found → create it)
 *   3. POST /api/v1/admin/dlq/replay/{id} → re-publishes payload to original topic
 *   4. POST /api/v1/admin/dlq/ignore/{id} → discard permanently bad messages
 */
@Service
public class DLQReplayService {

    private final DeadLetterEventRepository repository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    public DLQReplayService(
            DeadLetterEventRepository repository,
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> stringKafkaTemplate) {
        this.repository          = repository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    public void replay(Long dlqEventId) {
        DeadLetterEvent event = repository.findById(dlqEventId)
                .orElseThrow(() -> new RuntimeException(
                        "DLQ event not found: " + dlqEventId));

        if (event.getStatus() == DLQStatus.REPLAYED) {
            throw new IllegalStateException("Event already replayed: " + dlqEventId);
        }
        if (event.getStatus() == DLQStatus.IGNORED) {
            throw new IllegalStateException("Event was marked as ignored: " + dlqEventId);
        }

        // transfer-requested-topic.DLT → transfer-requested-topic
        String originalTopic = event.getTopic().replace(".DLT", "");
        stringKafkaTemplate.send(originalTopic, event.getPayload());

        event.setStatus(DLQStatus.REPLAYED);
        event.setResolvedAt(LocalDateTime.now());
        repository.save(event);

        System.out.println("🔁 [DLQ REPLAY] id=" + dlqEventId
                + " → topic=" + originalTopic);
    }

    public void replayAll() {
        List<DeadLetterEvent> pending = repository.findByStatus(DLQStatus.PENDING);
        System.out.println("🔁 [DLQ REPLAY ALL] count=" + pending.size());
        pending.forEach(e -> replay(e.getId()));
    }

    public void ignore(Long dlqEventId) {
        DeadLetterEvent event = repository.findById(dlqEventId)
                .orElseThrow(() -> new RuntimeException(
                        "DLQ event not found: " + dlqEventId));
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
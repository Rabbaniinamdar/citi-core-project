package com.citicore.account.controller;

import com.citicore.account.entity.DeadLetterEvent;
import com.citicore.account.service.DLQReplayService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for Dead Letter Queue management in account-service.
 *
 * DLQ events are created when a Kafka consumer fails after all retries
 * (3x exponential backoff: 2s → 4s → 8s → DLT → saved here).
 *
 * In production, secure with: @PreAuthorize("hasRole('ADMIN')")
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RefreshScope
public class DLQController {

    private final DLQReplayService dlqReplayService;

    public DLQController(DLQReplayService dlqReplayService) {
        this.dlqReplayService = dlqReplayService;
    }

    /** GET /api/v1/admin/dlq/pending — all PENDING DLQ events */
    @GetMapping("/pending")
    public ResponseEntity<List<DeadLetterEvent>> getPending() {
        return ResponseEntity.ok(dlqReplayService.getAllPending());
    }

    /** GET /api/v1/admin/dlq/all — all DLQ events regardless of status */
    @GetMapping("/all")
    public ResponseEntity<List<DeadLetterEvent>> getAll() {
        return ResponseEntity.ok(dlqReplayService.getAll());
    }

    /** POST /api/v1/admin/dlq/replay/{id} — replay single event to original topic */
    @PostMapping("/replay/{id}")
    public ResponseEntity<String> replay(@PathVariable Long id) {
        dlqReplayService.replay(id);
        return ResponseEntity.ok("✅ Replayed DLQ event id=" + id);
    }

    /** POST /api/v1/admin/dlq/replay/all — replay all PENDING events */
    @PostMapping("/replay/all")
    public ResponseEntity<String> replayAll() {
        dlqReplayService.replayAll();
        return ResponseEntity.ok("✅ All pending DLQ events replayed");
    }

    /** POST /api/v1/admin/dlq/ignore/{id} — mark event as IGNORED */
    @PostMapping("/ignore/{id}")
    public ResponseEntity<String> ignore(@PathVariable Long id) {
        dlqReplayService.ignore(id);
        return ResponseEntity.ok("🚫 DLQ event id=" + id + " marked as IGNORED");
    }
}
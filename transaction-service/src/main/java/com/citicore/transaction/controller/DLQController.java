package com.citicore.transaction.controller;

import com.citicore.transaction.entity.DeadLetterEvent;
import com.citicore.transaction.service.DLQReplayService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for Dead Letter Queue management.
 *
 * In production, secure these endpoints with role-based access:
 *   @PreAuthorize("hasRole('ADMIN')")
 *
 * Workflow:
 *   1. GET  /pending        → inspect all failed messages
 *   2. POST /replay/{id}    → fix root cause, then replay single event
 *   3. POST /replay/all     → replay all pending (after outage recovery)
 *   4. POST /ignore/{id}    → discard permanently bad/irrelevant data
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RefreshScope
public class DLQController {

    private final DLQReplayService dlqReplayService;

    public DLQController(DLQReplayService dlqReplayService) {
        this.dlqReplayService = dlqReplayService;
    }

    /** GET /api/v1/admin/dlq/pending — list all PENDING DLQ events */
    @GetMapping("/pending")
    public ResponseEntity<List<DeadLetterEvent>> getPending() {
        return ResponseEntity.ok(dlqReplayService.getAllPending());
    }

    /** GET /api/v1/admin/dlq/all — list every DLQ event regardless of status */
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

    /** POST /api/v1/admin/dlq/ignore/{id} — mark event as IGNORED (bad data) */
    @PostMapping("/ignore/{id}")
    public ResponseEntity<String> ignore(@PathVariable Long id) {
        dlqReplayService.ignore(id);
        return ResponseEntity.ok("🚫 DLQ event id=" + id + " marked as IGNORED");
    }
}
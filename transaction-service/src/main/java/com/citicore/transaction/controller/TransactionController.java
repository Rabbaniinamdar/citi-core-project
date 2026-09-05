package com.citicore.transaction.controller;

import com.citicore.transaction.dto.*;
import com.citicore.transaction.entity.TransactionStatus;
import com.citicore.transaction.service.TransactionService;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transaction")
@RefreshScope
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }


    /**
     * POST /api/v1/transaction/transfer
     * Initiates a fund transfer saga.
     * Returns txnRef immediately — saga runs asynchronously via Kafka.
     */
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<String>> transfer(
            @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.transfer(request));
    }

    /**
     * GET /api/v1/transaction/status/{txnRef}
     * Polls the current status of a transaction.
     * Client can poll this after receiving txnRef from /transfer.
     */
    @GetMapping("/status/{txnRef}")
    public ResponseEntity<ApiResponse<TransactionStatus>> getStatus(
            @PathVariable String txnRef) {
        return ResponseEntity.ok(transactionService.getStatus(txnRef));
    }

    /**
     * GET /api/v1/transaction/history
     * Returns paginated transaction history for the authenticated user.
     * Identity comes from JWT — no userId in path (avoids IDOR vulnerability).
     *
     * @param status optional filter (INITIATED / CREDIT_SUCCESS / FAILED / REVERSED)
     * @param page   zero-indexed page number (default 0)
     * @param size   page size (default 10)
     */
    @GetMapping("/history")
    public ResponseEntity<PageResponse<TransactionResponse>> history(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(transactionService.getHistory(status, page, size));
    }

    /**
     * GET /api/v1/transaction/{txnRef}
     * Returns full details of a single transaction by reference.
     */
    @GetMapping("/{txnRef}")
    public ResponseEntity<TransactionResponse> getByRef(
            @PathVariable String txnRef) {
        return ResponseEntity.ok(transactionService.getByReference(txnRef));
    }
}
package com.citicore.transaction.service;

import com.citicore.events.transaction.TransactionRequestedEvent;
import com.citicore.transaction.client.AccountFeignClient;
import com.citicore.transaction.dto.*;
import com.citicore.transaction.entity.*;
import com.citicore.transaction.exception.DailyLimitExceededException;
import com.citicore.transaction.repository.OutboxRepository;
import com.citicore.transaction.repository.TransactionRepository;
import com.citicore.transaction.util.TxnRefGenerator;
import com.citicore.transaction.entity.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountFeignClient accountFeignClient;
    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final TxnRefGenerator txnRefGenerator;
    private final TransactionLimitValidator transactionLimitValidator;
    private final ObjectMapper objectMapper;

    public TransactionService(
            AccountFeignClient accountFeignClient,
            TransactionRepository transactionRepository,
            OutboxRepository outboxRepository,
            TxnRefGenerator txnRefGenerator,
            TransactionLimitValidator transactionLimitValidator,
            ObjectMapper objectMapper) {
        this.accountFeignClient       = accountFeignClient;
        this.transactionRepository    = transactionRepository;
        this.outboxRepository         = outboxRepository;
        this.txnRefGenerator          = txnRefGenerator;
        this.transactionLimitValidator = transactionLimitValidator;
        this.objectMapper             = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TRANSFER — saga entry point
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Initiates a fund transfer saga.
     *
     * Flow:
     *  1. Validate request fields
     *  2. Calculate today's transfer total for daily limit check
     *  3. Feign pre-validation (fail-fast: check balance before starting saga)
     *  4. Validate daily limit
     *  5. Save Transaction(INITIATED) + OutboxEvent in ONE DB transaction (atomic)
     *  6. OutboxPublisher polls every 5s and publishes to transfer-requested-topic
     *  7. DebitConsumer picks it up → saga begins
     *
     * @return ApiResponse containing the generated txnRef
     */
    @Transactional
    public ApiResponse<String> transfer(TransactionRequest req) {

        // ── Step 1: Basic validation ──────────────────────────────────────────
        if (req == null || req.isInvalid()) {
            return ApiResponse.failure("Invalid transfer request", "");
        }

        // ── Step 2: Daily total for limit check ───────────────────────────────
        AuthUser authUser = getAuthUser();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = startOfDay.plusDays(1);

        BigDecimal todayTotal = transactionRepository.getTodayTotal(
                authUser.getId(), startOfDay, endOfDay);

        // ── Step 3: Feign pre-validation — fail-fast ──────────────────────────
        // Non-locking balance check: saves saga overhead on obvious failures.
        // The actual locking debit happens inside DebitConsumer (pessimistic lock).
        try {
            accountFeignClient.validateTransfer(req.getFromAccount(), req.getAmount());
        } catch (FeignException ex) {
            String message = extractFeignErrorMessage(ex);
            return ApiResponse.failure(String.valueOf(ex.status()), message);
        }

        // ── Step 4: Daily limit check ─────────────────────────────────────────
        try {
            transactionLimitValidator.validate(req.getType(), req.getAmount(), todayTotal);
        } catch (DailyLimitExceededException ex) {
            return ApiResponse.failure("Daily limit exceeded", ex.getMessage());
        }

        // ── Step 5: Atomic DB write — Transaction + OutboxEvent ───────────────
        String txnRef = txnRefGenerator.generate();

        Transaction txn = Transaction.builder()
                .txnId(txnRef)
                .authUserId(authUser.getId())
                .fromAccount(req.getFromAccount())
                .toAccount(req.getToAccount())
                .amount(req.getAmount())
                .type(req.getType())
                .status(TransactionStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(txn);

        // Serialize event to JSON string — OutboxPublisher sends via StringSerializer
        TransactionRequestedEvent event = new TransactionRequestedEvent(
                txnRef,
                req.getFromAccount(),
                req.getToAccount(),
                req.getAmount(),
                authUser.getId()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TransactionRequestedEvent", e);
        }

        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID().toString(),
                txnRef,                          // aggregate ID = partition key
                "transfer-requested-topic",      // target Kafka topic
                payload,                         // raw JSON string
                OutboxStatus.PENDING,
                LocalDateTime.now()
        ));

        // Both transaction + outbox event committed atomically by @Transactional.
        // OutboxPublisher will pick this up within 5 seconds.
        System.out.println("📤 [TRANSFER INITIATED] txnRef=" + txnRef);

        return ApiResponse.success("Transfer initiated successfully", txnRef);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────────

    public ApiResponse<TransactionStatus> getStatus(String txnRef) {
        Transaction txn = transactionRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnRef));
        return ApiResponse.success("Status fetched", txn.getStatus());
    }

    public TransactionResponse getByReference(String txnRef) {
        Transaction txn = transactionRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnRef));
        return mapToResponse(txn);
    }

    /**
     * Returns paginated transaction history for the authenticated user.
     *
     * Bug fix: null-check on status BEFORE calling TransactionStatus.valueOf()
     * — the original code called valueOf(null) first, always throwing IllegalArgumentException.
     */
    public PageResponse<TransactionResponse> getHistory(String status, int page, int size) {

        AuthUser authUser = getAuthUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Transaction> txns;

        if (status != null && !status.isBlank()) {
            TransactionStatus txnStatus;
            try {
                txnStatus = TransactionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status value: " + status
                        + ". Valid values: INITIATED, CREDIT_SUCCESS, FAILED, REVERSED");
            }
            txns = transactionRepository.findByAuthUserIdAndStatus(
                    authUser.getId(), txnStatus, pageable);
        } else {
            txns = transactionRepository.findByAuthUserId(authUser.getId(), pageable);
        }

        PageResponse<TransactionResponse> response = new PageResponse<>();
        response.setContent(txns.getContent().stream().map(this::mapToResponse).toList());
        response.setPage(txns.getNumber());
        response.setSize(txns.getSize());
        response.setTotalElements(txns.getTotalElements());
        response.setTotalPages(txns.getTotalPages());
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private TransactionResponse mapToResponse(Transaction txn) {
        return TransactionResponse.builder()
                .txnId(txn.getTxnRef())
                .fromAccount(txn.getFromAccount())
                .toAccount(txn.getToAccount())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .createdAt(txn.getCreatedAt())
                .build();
    }

    private AuthUser getAuthUser() {
        return (AuthUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    /**
     * Extracts the "message" field from a Feign error response body.
     * Falls back to "Request failed" if body is missing or malformed.
     */
    private String extractFeignErrorMessage(FeignException ex) {
        try {
            String body = ex.contentUTF8();
            if (body != null && !body.isBlank()) {
                JsonNode node = objectMapper.readTree(body);
                if (node.has("message")) {
                    return node.get("message").asText();
                }
            }
        } catch (Exception ignored) {}
        return "Request failed";
    }
}
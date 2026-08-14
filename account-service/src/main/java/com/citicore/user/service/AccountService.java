package com.citicore.user.service;

import com.citicore.user.client.UserClient;
import com.citicore.user.entity.*;
import com.citicore.user.exception.*;
import com.citicore.user.repository.*;
import com.citicore.events.account.AccountEvent;
import com.citicore.events.account.AccountEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AccountService {

    private final AccountRepository            accountRepository;
    private final AccountStatementRepository   statementRepository;
    private final AccountNumberGenerator       numberGenerator;
    private final UserClient                   userClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountOutboxRepository      accountOutboxRepository;
    private final ObjectMapper                 objectMapper;

    @Value("${citicore.account.min-savings-balance}")
    private BigDecimal minSavings;

    @Value("${citicore.account.min-current-balance}")
    private BigDecimal minCurrent;

    public AccountService(
            AccountRepository accountRepository,
            AccountStatementRepository statementRepository,
            AccountNumberGenerator numberGenerator,
            UserClient userClient,
            RedisTemplate<String, Object> redisTemplate,
            AccountOutboxRepository accountOutboxRepository,
            ObjectMapper objectMapper) {
        this.accountRepository      = accountRepository;
        this.statementRepository    = statementRepository;
        this.numberGenerator        = numberGenerator;
        this.userClient             = userClient;
        this.redisTemplate          = redisTemplate;
        this.accountOutboxRepository = accountOutboxRepository;
        this.objectMapper           = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CREATE ACCOUNT
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new bank account for the authenticated user.
     *
     * Prerequisites:
     *   - KYC must be completed (checked via Feign call to user-service)
     *   - User must not already have an account of this type
     *
     * Side effects:
     *   - Saves Account record
     *   - Saves opening-deposit AccountStatement record
     *   - Saves AccountOutboxEvent → publishes ACCOUNT_CREATED to account-events-topic
     *     (notification-service sends welcome email)
     */
    @Transactional
    public Account createAccount(AccountType type,
                                 BigDecimal initialDeposit,
                                 Long userId,
                                 String email) {

        // ── KYC gate ──────────────────────────────────────────────────────────
        if (!userClient.getKycStatus(userId)) {
            throw new KycNotCompletedException(
                    "KYC not completed. Please complete KYC before creating an account.");
        }

        // ── Duplicate account guard ───────────────────────────────────────────
        if (accountRepository.existsByAuthUserIdAndAccountType(userId, type)) {
            throw new DuplicateAccountException(
                    "Account already exists for type: " + type);
        }

        // ── First account becomes the default ─────────────────────────────────
        boolean isFirstAccount = accountRepository.findByAuthUserId(userId).isEmpty();

        String accNo   = numberGenerator.generateAccountNumber();
        Account account = new Account.Builder()
                .accountNumber(accNo)
                .authUserId(userId)
                .accountType(type)
                .balance(initialDeposit)
                .isDefault(isFirstAccount)
                .build();

        accountRepository.save(account);

        // ── Opening deposit statement ─────────────────────────────────────────
        String openingTxnRef = "ACC_OPEN_" + UUID.randomUUID();
        statementRepository.save(new AccountStatement.Builder()
                .txnRef(openingTxnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.CREDIT)
                .amount(initialDeposit)
                .balanceAfterTxn(initialDeposit)
                .description("Account Opening Deposit")
                .build());

        // ── Outbox event → notification-service sends welcome email ───────────
        AccountEvent event = new AccountEvent(
                AccountEventType.ACCOUNT_CREATED,
                accNo,
                email,
                initialDeposit,
                null
        );
        saveToOutbox(event, accNo, "account-events-topic");

        System.out.println("✅ [ACCOUNT CREATED] accNo=" + accNo
                + " | type=" + type + " | userId=" + userId);

        return account;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DEBIT
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Debits an account as part of the transfer saga.
     *
     * Called by DebitConsumer after receiving transfer-requested-topic event.
     *
     * Guards:
     *   1. Idempotency — checks txnRef in account_statements before processing
     *      (prevents double-debit on Kafka at-least-once redelivery)
     *   2. Pessimistic lock — SELECT ... FOR UPDATE on account row
     *      (prevents race condition with concurrent transfers)
     *   3. Ownership — verifies authUserId matches account owner
     *   4. Balance — ensures sufficient balance
     *   5. Minimum balance — enforces product-level minimum balance rules
     *
     * After debit:
     *   - Updates Redis cache with new balance (TTL 5 min)
     *   - Saves AccountStatement with unique txnRef
     *   - Saves outbox event → account-events-topic (debit alert email)
     */
    @Transactional
    public void debit(String accNo, BigDecimal amount, String txnRef,
                      Long authUserId, String email) {

        // ── 1. Idempotency check ──────────────────────────────────────────────
        if (statementRepository.existsByTxnRef(txnRef)) {
            System.out.println("⚠️ [DEBIT] Duplicate txnRef=" + txnRef + " — skipping");
            return;
        }

        // ── 2. Pessimistic lock ───────────────────────────────────────────────
        Account account = accountRepository
                .findByAccountNumberWithLock(accNo)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accNo));

        System.out.println("💸 [DEBIT] txnRef=" + txnRef
                + " | acc=" + accNo
                + " | amount=" + amount
                + " | balance=" + account.getBalance());

        // ── 3. Ownership check ────────────────────────────────────────────────
        if (!account.getAuthUserId().equals(authUserId)) {
            throw new UnauthorizedAccountAccessException(
                    "Unauthorized: account does not belong to user " + authUserId);
        }

        // ── 4. Balance check ──────────────────────────────────────────────────
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: ₹" + account.getBalance()
                            + " | Requested: ₹" + amount);
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);

        // ── 5. Minimum balance check ──────────────────────────────────────────
        if (account.getAccountType() == AccountType.SAVINGS
                && newBalance.compareTo(minSavings) < 0) {
            throw new MinimumBalanceViolationException(
                    "Minimum savings balance of ₹" + minSavings + " must be maintained.");
        }
        if (account.getAccountType() == AccountType.CURRENT
                && newBalance.compareTo(minCurrent) < 0) {
            throw new MinimumBalanceViolationException(
                    "Minimum current balance of ₹" + minCurrent + " must be maintained.");
        }

        // ── Update balance ────────────────────────────────────────────────────
        account.setBalance(newBalance);
        accountRepository.save(account);

        // ── Redis cache update ────────────────────────────────────────────────
        String cacheKey = "account:" + accNo + ":balance";
        redisTemplate.opsForValue().set(cacheKey, newBalance, 5, TimeUnit.MINUTES);

        // ── Statement record ──────────────────────────────────────────────────
        statementRepository.save(new AccountStatement.Builder()
                .txnRef(txnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfterTxn(newBalance)
                .description("Debit Transaction")
                .build());

        // ── Outbox event → notification-service sends debit alert email ───────
        AccountEvent event = new AccountEvent(
                AccountEventType.ACCOUNT_DEBITED,
                accNo,
                email,
                amount,
                txnRef
        );
        saveToOutbox(event, accNo, "account-events-topic");

        System.out.println("✅ [DEBIT SUCCESS] txnRef=" + txnRef
                + " | newBalance=" + newBalance);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CREDIT
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Credits an account as part of the transfer saga or reversal.
     *
     * Called by:
     *   - CreditConsumer (transfer saga — credits receiver)
     *   - ReversalConsumer (compensating transaction — credits back sender)
     *
     * Guards:
     *   1. Idempotency — txnRef uniqueness check in account_statements
     *   2. Pessimistic lock — SELECT ... FOR UPDATE
     *
     * After credit:
     *   - Updates Redis cache with new balance
     *   - Saves AccountStatement with unique txnRef
     *   - Saves outbox event → account-events-topic (credit alert email)
     */
    @Transactional
    public void credit(String accNo, BigDecimal amount, String txnRef,
                       Long authUserId, String email) {

        // ── 1. Idempotency check ──────────────────────────────────────────────
        if (statementRepository.existsByTxnRef(txnRef)) {
            System.out.println("⚠️ [CREDIT] Duplicate txnRef=" + txnRef + " — skipping");
            return;
        }

        // ── 2. Pessimistic lock ───────────────────────────────────────────────
        Account account = accountRepository
                .findByAccountNumberWithLock(accNo)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accNo));

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        // ── Redis cache update ────────────────────────────────────────────────
        String cacheKey = "account:" + accNo + ":balance";
        redisTemplate.opsForValue().set(cacheKey, newBalance, 5, TimeUnit.MINUTES);

        // ── Statement record ──────────────────────────────────────────────────
        statementRepository.save(new AccountStatement.Builder()
                .txnRef(txnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.CREDIT)
                .amount(amount)
                .balanceAfterTxn(newBalance)
                .description("Credit Transaction")
                .build());

        // ── Outbox event → notification-service sends credit alert email ──────
        AccountEvent event = new AccountEvent(
                AccountEventType.ACCOUNT_CREDITED,
                accNo,
                email,
                amount,
                txnRef
        );
        saveToOutbox(event, accNo, "account-events-topic");

        System.out.println("✅ [CREDIT SUCCESS] txnRef=" + txnRef
                + " | newBalance=" + newBalance);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DEPOSIT & WITHDRAW (delegating to debit/credit)
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public void deposit(String accNo, BigDecimal amount,
                        String txnRef, Long authUserId, String email) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        credit(accNo, amount, txnRef, authUserId, email);
    }

    @Transactional
    public void withdraw(String accNo, BigDecimal amount,
                         String txnRef, Long authUserId, String email) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        debit(accNo, amount, txnRef, authUserId, email);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BALANCE — Cache-Aside pattern
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns account balance.
     *
     * Cache-Aside pattern:
     *   1. Check Redis — cache HIT → return instantly (no DB query)
     *   2. Cache MISS → query PostgreSQL → populate Redis (TTL 5 min) → return
     *
     * Redis is proactively updated after every debit/credit so cache stays
     * fresh without waiting for TTL expiry.
     */
    public BigDecimal getBalance(String accNo) {
        String cacheKey = "account:" + accNo + ":balance";

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            System.out.println("⚡ [BALANCE] Cache HIT for acc=" + accNo);
            return new BigDecimal(cached.toString());
        }

        System.out.println("🗄️ [BALANCE] Cache MISS — querying DB for acc=" + accNo);
        Account account = accountRepository
                .findByAccountNumber(accNo)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accNo));

        // Populate cache for future reads
        redisTemplate.opsForValue().set(cacheKey, account.getBalance(), 5, TimeUnit.MINUTES);

        return account.getBalance();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRE-VALIDATION (called by transaction-service via Feign)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Non-locking pre-validation used by transaction-service before starting the saga.
     *
     * Provides fail-fast: if balance is insufficient, transaction-service returns
     * an error immediately without initiating the Kafka saga at all.
     *
     * This is a READ-ONLY check — no lock, no state change.
     * The actual locking debit happens inside DebitConsumer with pessimistic lock.
     */
    public void validateTransfer(String accNo, BigDecimal amount) {
        Account acc = accountRepository.findByAccountNumber(accNo)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accNo));

        if (acc.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: ₹" + acc.getBalance());
        }

        BigDecimal newBalance = acc.getBalance().subtract(amount);

        if (acc.getAccountType() == AccountType.SAVINGS
                && newBalance.compareTo(minSavings) < 0) {
            throw new MinimumBalanceViolationException(
                    "Transfer would breach minimum savings balance of ₹" + minSavings);
        }
        if (acc.getAccountType() == AccountType.CURRENT
                && newBalance.compareTo(minCurrent) < 0) {
            throw new MinimumBalanceViolationException(
                    "Transfer would breach minimum current balance of ₹" + minCurrent);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────────

    public List<Account> getMyAccounts(Long userId) {
        return accountRepository.findByAuthUserId(userId);
    }

    public Account getDefaultAccount(Long userId) {
        return accountRepository
                .findByAuthUserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No default account found for user: " + userId));
    }

    public BigDecimal getMyBalance(Long userId) {
        return getDefaultAccount(userId).getBalance();
    }

    public List<AccountStatement> getMiniStatement(String accNo) {
        return statementRepository
                .findTop10ByAccountNumberOrderByCreatedAtDesc(accNo);
    }

    public Page<AccountStatement> getStatement(String accNo, int page, int size) {
        return statementRepository
                .findByAccountNumberOrderByCreatedAtDesc(
                        accNo, PageRequest.of(page, size));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Saves an event to the account_outbox table.
     *
     * The AccountOutboxPublisher polls this table every 5 seconds and publishes
     * PENDING records to Kafka using KafkaTemplate<String,String> with StringSerializer.
     *
     * CRITICAL: payload is serialized to JSON string here.
     * AccountOutboxPublisher sends it via StringSerializer → no double encoding.
     */
    private void saveToOutbox(Object event, String accountNumber, String topic) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            AccountOutboxEvent outbox = new AccountOutboxEvent();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setAccountNumber(accountNumber);
            outbox.setTopic(topic);
            outbox.setPayload(payload);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setCreatedAt(LocalDateTime.now());

            accountOutboxRepository.save(outbox);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}
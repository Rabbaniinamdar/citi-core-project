package com.citicore.account.service;

import com.citicore.account.config.PrimaryRead;
import com.citicore.account.config.ReadOnly;
import com.citicore.account.entity.*;
import com.citicore.account.exception.*;
import com.citicore.account.repository.*;
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

/**
 * AccountService — updated with read/write routing.
 *
 * Write methods (@Transactional):
 *   createAccount, debit, credit, deposit, withdraw
 *   → No @ReadOnly → RoutingDataSource defaults to PRIMARY ✅
 *
 * Read methods (@ReadOnly):
 *   getBalance, getMiniStatement, getStatement,
 *   validateTransfer, getMyAccounts, getDefaultAccount
 *   → @ReadOnly → AOP sets REPLICA before execution ✅
 *
 * NOTE: getBalance() is backed by Redis cache.
 * A cache HIT never touches any DataSource at all.
 * Only a cache MISS goes to the REPLICA.
 * So in practice the replica mostly serves:
 *   - getMiniStatement (always DB)
 *   - getStatement (always DB)
 *   - validateTransfer (always DB — pre-validation before saga)
 */
@Service
public class AccountService {

    private final AccountRepository           accountRepository;
    private final AccountStatementRepository  statementRepository;
    private final AccountNumberGenerator      numberGenerator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountOutboxRepository     accountOutboxRepository;
    private final ObjectMapper                objectMapper;

    @Value("${citicore.account.min-savings-balance}")
    private BigDecimal minSavings;

    @Value("${citicore.account.min-current-balance}")
    private BigDecimal minCurrent;

    public AccountService(
            AccountRepository accountRepository,
            AccountStatementRepository statementRepository,
            AccountNumberGenerator numberGenerator,
            RedisTemplate<String, Object> redisTemplate,
            AccountOutboxRepository accountOutboxRepository,
            ObjectMapper objectMapper) {
        this.accountRepository      = accountRepository;
        this.statementRepository    = statementRepository;
        this.numberGenerator        = numberGenerator;
        this.redisTemplate          = redisTemplate;
        this.accountOutboxRepository = accountOutboxRepository;
        this.objectMapper           = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITES → PRIMARY DataSource
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Account createAccount(AccountType type, BigDecimal initialDeposit,
                                 Long userId, String email) {

        if (accountRepository.existsByAuthUserIdAndAccountType(userId, type)) {
            throw new DuplicateAccountException(
                    "Account already exists for type: " + type);
        }

        boolean isFirstAccount = accountRepository.findByAuthUserId(userId).isEmpty();
        String accNo = numberGenerator.generateAccountNumber();

        Account account = new Account.Builder()
                .accountNumber(accNo)
                .authUserId(userId)
                .accountType(type)
                .balance(initialDeposit)
                .isDefault(isFirstAccount)
                .build();

        accountRepository.save(account);

        String openingTxnRef = "ACC_OPEN_" + UUID.randomUUID();
        statementRepository.save(new AccountStatement.Builder()
                .txnRef(openingTxnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.CREDIT)
                .amount(initialDeposit)
                .balanceAfterTxn(initialDeposit)
                .description("Account Opening Deposit")
                .build());

        saveToOutbox(new AccountEvent(
                        AccountEventType.ACCOUNT_CREATED, accNo, email, initialDeposit, null),
                accNo, "account-events-topic");

        System.out.println("✅ [PRIMARY WRITE] createAccount accNo=" + accNo);
        return account;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READS → REPLICA DataSource (via @ReadOnly AOP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cache-Aside + Strong Read:
     *
     * HIT  → Redis returns balance
     * MISS → PRIMARY MySQL
     *
     * PRIMARY is intentional because balance is financially sensitive
     * and we don't want replica lag to return stale balance.
     */
    @PrimaryRead
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accNo) {
        String cacheKey = "account:" + accNo + ":balance";

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            System.out.println("⚡ [CACHE HIT] balance for acc=" + accNo);
            return new BigDecimal(cached.toString());
        }

        System.out.println("📖 [REPLICA READ] getBalance cache MISS acc=" + accNo);
        Account account = accountRepository
                .findByAccountNumber(accNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

        // Populate cache for future reads
        redisTemplate.opsForValue().set(cacheKey, account.getBalance(), 5, TimeUnit.MINUTES);
        return account.getBalance();
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public void validateTransfer(String accNo, BigDecimal amount) {
        System.out.println("📖 [REPLICA READ] validateTransfer acc=" + accNo);

        Account acc = accountRepository.findByAccountNumber(accNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

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

    @ReadOnly
    @Transactional(readOnly = true)
    public List<Account> getMyAccounts(Long userId) {
        System.out.println("📖 [REPLICA READ] getMyAccounts userId=" + userId);
        return accountRepository.findByAuthUserId(userId);
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public Account getDefaultAccount(Long userId) {
        System.out.println("📖 [REPLICA READ] getDefaultAccount userId=" + userId);
        return accountRepository
                .findByAuthUserIdAndIsDefaultTrue(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No default account found for user: " + userId));
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public List<AccountStatement> getMiniStatement(String accNo) {
        System.out.println("📖 [REPLICA READ] getMiniStatement acc=" + accNo);
        return statementRepository
                .findTop10ByAccountNumberOrderByCreatedAtDesc(accNo);
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public Page<AccountStatement> getStatement(String accNo, int page, int size) {
        System.out.println("📖 [REPLICA READ] getStatement acc=" + accNo
                + " page=" + page);
        return statementRepository
                .findByAccountNumberOrderByCreatedAtDesc(
                        accNo, PageRequest.of(page, size));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void debit(String accNo, BigDecimal amount, String txnRef,
                      Long authUserId, String email) {
        validateAmount(amount);

        if (statementRepository.existsByTxnRef(txnRef)) {
            System.out.println("⚠️ [DEBIT] Duplicate txnRef=" + txnRef + " — skipping");
            return;
        }

        Account account = accountRepository
                .findByAccountNumberWithLock(accNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

        if (!account.getAuthUserId().equals(authUserId)) {
            throw new UnauthorizedAccountAccessException(
                    "Unauthorized: account does not belong to user " + authUserId);
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        checkMinimumBalance(account.getAccountType(), newBalance);

        account.setBalance(newBalance);
        accountRepository.save(account);

        redisTemplate.opsForValue().set(
                "account:" + accNo + ":balance", newBalance, 5, TimeUnit.MINUTES);

        statementRepository.save(new AccountStatement.Builder()
                .txnRef(txnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfterTxn(newBalance)
                .description("Debit Transaction")
                .build());

        saveToOutbox(new AccountEvent(
                        AccountEventType.ACCOUNT_DEBITED, accNo, email, amount, txnRef),
                accNo, "account-events-topic");
    }

    // NOTE: same lock method as debit() — this is the critical fix.
    // Ownership check is intentionally NOT added here — see deposit() below
    // for where that decision needs to be made.
    @Transactional
    public void credit(String accNo, BigDecimal amount, String txnRef,
                       Long authUserId, String email) {
        validateAmount(amount);

        if (statementRepository.existsByTxnRef(txnRef)) {
            System.out.println("⚠️ [CREDIT] Duplicate txnRef=" + txnRef + " — skipping");
            return;
        }

        Account account = accountRepository
                .findByAccountNumberWithLock(accNo)   // <-- was findByAccountNumber (no lock)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        redisTemplate.opsForValue().set(
                "account:" + accNo + ":balance", newBalance, 5, TimeUnit.MINUTES);

        statementRepository.save(new AccountStatement.Builder()
                .txnRef(txnRef)
                .accountNumber(accNo)
                .transactionType(TransactionType.CREDIT)
                .amount(amount)
                .balanceAfterTxn(newBalance)
                .description("Credit Transaction")
                .build());

        saveToOutbox(new AccountEvent(
                        AccountEventType.ACCOUNT_CREDITED, accNo, email, amount, txnRef),
                accNo, "account-events-topic");
    }

    @Transactional
    public void deposit(String accNo, BigDecimal amount,
                        String txnRef, Long authUserId, String email) {
        validateAmount(amount);
        // Decision needed: if deposit is self-service top-up only, uncomment:
        // if (!accountRepository.existsByAccountNumberAndAuthUserId(accNo, authUserId)) {
        //     throw new UnauthorizedAccountAccessException(
        //             "Unauthorized: account does not belong to user " + authUserId);
        // }
        credit(accNo, amount, txnRef, authUserId, email);
    }

    @Transactional
    public void withdraw(String accNo, BigDecimal amount,
                         String txnRef, Long authUserId, String email) {
        validateAmount(amount);
        debit(accNo, amount, txnRef, authUserId, email);
    }

    // ─── READS — now with ownership checks ───

    @PrimaryRead
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accNo, Long authUserId) {
        assertOwnership(accNo, authUserId);

        String cacheKey = "account:" + accNo + ":balance";
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return new BigDecimal(cached.toString());
        }

        Account account = accountRepository
                .findByAccountNumber(accNo)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

        redisTemplate.opsForValue().set(cacheKey, account.getBalance(), 5, TimeUnit.MINUTES);
        return account.getBalance();
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public List<AccountStatement> getMiniStatement(String accNo, Long authUserId) {
        assertOwnership(accNo, authUserId);
        return statementRepository.findTop10ByAccountNumberOrderByCreatedAtDesc(accNo);
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public Page<AccountStatement> getStatement(String accNo, int page, int size, Long authUserId) {
        assertOwnership(accNo, authUserId);
        return statementRepository.findByAccountNumberOrderByCreatedAtDesc(
                accNo, PageRequest.of(page, size));
    }

    @ReadOnly
    @Transactional(readOnly = true)
    public BigDecimal getMyBalance(Long userId) {
        // delegate to the cache-aware, primary-forced path instead of a raw replica read
        String accNo = getDefaultAccount(userId).getAccountNumber();
        return getBalance(accNo, userId);
    }

    // ─── PRIVATE HELPERS ───

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void checkMinimumBalance(AccountType type, BigDecimal newBalance) {
        if (type == AccountType.SAVINGS && newBalance.compareTo(minSavings) < 0) {
            throw new MinimumBalanceViolationException(
                    "Minimum savings balance of ₹" + minSavings + " must be maintained.");
        }
        if (type == AccountType.CURRENT && newBalance.compareTo(minCurrent) < 0) {
            throw new MinimumBalanceViolationException(
                    "Minimum current balance of ₹" + minCurrent + " must be maintained.");
        }
    }

    private void assertOwnership(String accNo, Long authUserId) {
        boolean owns = accountRepository.existsByAccountNumberAndAuthUserId(accNo, authUserId);
        if (!owns) {
            throw new UnauthorizedAccountAccessException(
                    "Unauthorized: account does not belong to user " + authUserId);
        }
    }
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
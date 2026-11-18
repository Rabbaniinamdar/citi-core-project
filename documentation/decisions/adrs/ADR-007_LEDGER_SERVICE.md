# ADR-007: Ledger Service — Separate Microservice

## Status
✅ **APPROVED** (2026-11-18)

## Context

The CitiCore platform requires a double-entry general ledger for regulatory compliance (SOX, IFRS, RBI). The current implementation uses single-entry bookkeeping via `AccountStatement` in the Account Service, which is insufficient for regulatory reporting, trial balance generation, and GL reconciliation.

**Requirements:**
- Double-entry bookkeeping with balanced journal entries
- Chart of Accounts with standard banking structure
- Trial balance generation
- Period-end close process
- GL reconciliation (sub-ledger to GL)
- Immutable audit trail
- Regulatory reporting (COA mapping)

## Decision

**Implement a separate Ledger Service microservice (Port 8086) for double-entry general ledger.**

### Service Responsibilities

| Responsibility | Description |
|----------------|-------------|
| **Chart of Accounts** | Manage GL accounts (Asset, Liability, Equity, Income, Expense) |
| **Journal Entry Engine** | Create, validate (balanced), post journal entries |
| **Sub-Ledger Integration** | Receive postings from Account Service, Transaction Service |
| **Trial Balance** | Generate periodic trial balance reports |
| **Period-End Close** | Monthly/quarterly close process |
| **GL Reconciliation** | Sub-ledger to GL reconciliation jobs |
| **Regulatory Reporting** | COA mapping for regulatory reports |

### Service Boundaries

| Service | Responsibility |
|---------|----------------|
| **Account Service** | Customer-facing sub-ledger (customer deposits, balances, statements) |
| **Transaction Service** | Fund transfer orchestration, saga orchestration |
| **Ledger Service** | General Ledger (regulatory view), double-entry posting, GL reconciliation |
| **Account Service → Ledger** | Calls Ledger Service via Feign for journal posting |

### Data Model

```java
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", unique = true, nullable = false)
    private String code; // 1000, 2000-12345...
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type; // ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    
    @Column(name = "currency", nullable = false)
    private String currency; // INR
    
    @Column(name = "is_control_account")
    private Boolean isControlAccount; // Links to sub-ledger
    
    @Column(name = "parent_code")
    private String parentCode; // For hierarchy
}

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "txn_ref", unique = true, nullable = false)
    private String txnRef; // Business transaction reference
    
    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JournalStatus status; // POSTED, REVERSED
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL)
    private List<JournalLine> lines;
}

@Entity
@Table(name = "journal_lines")
public class JournalLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "journal_id", nullable = false)
    private JournalEntry journalEntry;
    
    @ManyToOne
    @JoinColumn(name = "account_code", nullable = false)
    private LedgerAccount account;
    
    @Column(name = "debit", precision = 19, scale = 4)
    private BigDecimal debit; // >= 0
    
    @Column(name = "credit", precision = 19, scale = 4)
    private BigDecimal credit; // >= 0
    
    // Exactly one of debit/credit > 0
}
```

### Journal Posting Rules

| Business Event | Debit Account | Credit Account | Description |
|----------------|---------------|----------------|-------------|
| Customer Deposit (Cash) | 1000 - Cash | 2000-{account} - Customer Deposits | Cash received |
| Customer Withdrawal (Cash) | 2000-{account} - Customer Deposits | 1000 - Cash | Cash paid out |
| Internal Transfer (A→B) | 2000-{A} - Customer Deposits | 2000-{B} - Customer Deposits | Liability transfer |
| Loan Disbursement | 1200 - Loans Receivable | 2000-{account} - Customer Deposits | Asset created, liability increased |
| Interest Accrual | 2000-{account} - Customer Deposits | 4000 - Interest Income | Expense to bank, income recognized |
| Fee Collection | 2000-{account} - Customer Deposits | 4100 - Fee Income | Liability reduced, income recognized |

### Invariants (Database Constraints)

```sql
-- Journal entry must balance (enforced by trigger)
CREATE TRIGGER trg_journal_balance
BEFORE INSERT ON journal_entries
FOR EACH ROW
BEGIN
    DECLARE v_debit DECIMAL(19,4);
    DECLARE v_credit DECIMAL(19,4);
    SELECT COALESCE(SUM(debit),0), COALESCE(SUM(credit),0) 
    INTO v_debit, v_credit
    FROM journal_lines WHERE journal_id = NEW.id;
    IF v_debit != v_credit THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Journal entry must balance';
    END IF;
END;

-- Journal line: exactly one side
ALTER TABLE journal_lines ADD CONSTRAINT chk_one_side 
CHECK ((debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0));

-- No negative amounts
ALTER TABLE journal_lines ADD CONSTRAINT chk_positive 
CHECK (debit >= 0 AND credit >= 0);

-- Account balance consistency (reconciliation view)
CREATE VIEW v_balance_reconciliation AS
SELECT 
    a.account_number,
    a.balance as account_balance,
    COALESCE(SUM(CASE WHEN s.transaction_type='CREDIT' THEN s.amount ELSE -s.amount END),0) as statement_balance,
    COALESCE(SUM(CASE WHEN jl.account_code=CONCAT('2000-',a.account_number) THEN jl.debit - jl.credit END),0) as ledger_balance
FROM accounts a
LEFT JOIN account_statements s ON a.account_number = s.account_number
LEFT JOIN journal_lines jl ON jl.account_code = CONCAT('2000-', a.account_number)
GROUP BY a.account_number, a.balance;
```

### Sub-Ledger to GL Mapping

| Sub-Ledger | Control Account | Description |
|------------|-----------------|-------------|
| Customer Deposits | 2000-{accountNumber} | Each customer account has sub-ledger |
| Cash/Vault | 1000 | Physical cash |
| Loans Receivable | 1200 | Loan assets |
| Interest Income | 4000 | Revenue |
| Fee Income | 4100 | Revenue |

### Integration with Account Service

```java
// Account Service calls Ledger Service via Feign
@FeignClient(name = "ledger-service")
public interface LedgerClient {
    
    @PostMapping("/api/v1/ledger/journal-entries")
    JournalEntryDto postJournalEntry(@RequestBody JournalEntryRequest request);
    
    @GetMapping("/api/v1/ledger/trial-balance")
    TrialBalanceDto getTrialBalance(@RequestParam LocalDate asOfDate);
    
    @GetMapping("/api/v1/ledger/reconciliation")
    ReconciliationReportDto getReconciliationReport(@RequestParam LocalDate asOfDate);
}

// Account Service posting
@Service
public class LedgerPostingService {
    
    private final LedgerClient ledgerClient;
    
    public void postDepositJournalEntry(Account account, BigDecimal amount, String txnRef) {
        JournalEntryRequest request = JournalEntryRequest.builder()
            .txnRef(txnRef)
            .entryDate(LocalDateTime.now())
            .description("Customer Deposit - " + account.getAccountNumber())
            .lines(List.of(
                JournalLineRequest.builder()
                    .accountCode("1000") // Cash
                    .debit(amount)
                    .build(),
                JournalLineRequest.builder()
                    .accountCode("2000-" + account.getAccountNumber()) // Customer Deposits
                    .credit(amount)
                    .build()
            ))
            .build();
        
        ledgerClient.postJournalEntry(request);
    }
}
```

### Reconciliation Job

```java
@Scheduled(cron = "0 0 2 * * ?") // Daily 2 AM
public void reconcileSubLedgerToGL() {
    List<ReconciliationDifference> differences = reconciliationService.findDifferences();
    
    if (!differences.isEmpty()) {
        alertService.sendAlert(Alert.builder()
            .type("GL_RECONCILIATION_MISMATCH")
            .title("Sub-Ledger to GL Reconciliation Mismatch")
            .details(differences)
            .severity(HIGH)
            .build());
    }
}
```

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) Separate Ledger Service (Selected)** | Dedicated microservice for GL | Regulatory independence; clear separation; independent scaling | Additional service overhead | ✅ **Selected** |
| **B) Embedded in Account Service** | GL logic within Account Service | Simpler deployment | Mixed responsibilities; regulatory risk; scaling coupling | ❌ Rejected |
| **C) Embedded in Transaction Service** | GL with Transaction Service | Close to fund transfers | Wrong domain; Transaction ≠ GL | ❌ Rejected |

## Consequences & Trade-offs

### Positive
- **Regulatory Independence**: Ledger Service can be audited separately
- **Clear Separation**: Account Service = sub-ledger (customer view); Ledger Service = GL (regulatory view)
- **Independent Scaling**: Ledger scales independently from account/transaction processing
- **Regulatory Compliance**: Meets SOX, IFRS, RBI requirements for separate GL

### Negative
- **Operational Overhead**: Additional service to deploy, monitor, maintain
- **Network Latency**: Additional Feign call for each journal posting
- **Complexity**: Distributed transaction coordination (saga for journal posting)

## Implementation Considerations

1. **Idempotency**: Journal entries idempotent via `txnRef` deduplication
2. **Saga for Journal Posting**: Account Service posts to Ledger via Feign; Ledger publishes `journal-posted` event
3. **Reconciliation**: Daily job comparing sub-ledger balances to GL control accounts
4. **Migration**: Phase 5 implementation (after core banking features)

## Dependencies

- **DEC-001**: Database Topology (separate schema `citicore_ledgerdb`)
- **DEC-002**: Saga Pattern (journal posting via saga)
- **DEC-001**: Database Topology (separate schema `citicore_ledgerdb`)

## Future Considerations

| Phase | Enhancement |
|-------|-------------|
| **Phase 5** | Multi-currency GL with FX revaluation |
| **Phase 5** | Automated regulatory report generation (COREP/FINREP) |
| **Phase 5** | Real-time GL analytics dashboard |
| **Phase 5** | Integration with external audit systems |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**
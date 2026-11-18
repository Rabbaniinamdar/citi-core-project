# CitiCore Banking Platform - Testing Strategy

## Executive Summary

This document defines a comprehensive testing strategy for the CitiCore platform, covering unit, integration, contract, performance, security, and chaos engineering tests. The strategy ensures **financial correctness**, **security**, and **reliability** at every layer.

**Testing Philosophy:** Test financial invariants, not just HTTP status codes.

---

## 1. Test Pyramid

```
                    ┌─────────────┐
                    │   E2E       │  ← 5%  (Critical user journeys)
                    │  (5 tests)  │
                   ┌┴─────────────┴┐
                  │  Contract     │  ← 15% (API contracts, Kafka schemas)
                  │  (20 tests)   │
                 ┌┴────────────────┴┐
                │  Integration     │  ← 30% (DB, Kafka, Feign, Redis)
                │  (100 tests)     │
               ┌┴────────────────────┴┐
              │     Unit             │  ← 50% (Pure logic, no infrastructure)
              │    (500+ tests)      │
             └────────────────────────┘
```

---

## 2. Unit Testing (JUnit 5 + Mockito)

### 2.1 Coverage Targets
| Layer | Target | Tool |
|-------|--------|------|
| Service Layer | 90% | JaCoCo |
| Domain Logic | 95% | JaCoCo |
| Utility Classes | 100% | JaCoCo |
| Controllers | 70% | MockMvc |
| Overall | 80% | JaCoCo |

### 2.2 Key Test Scenarios

#### AccountService Tests
```java
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    
    @Mock AccountRepository accountRepository;
    @Mock AccountStatementRepository statementRepository;
    @Mock AccountOutboxRepository outboxRepository;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock AccountNumberGenerator numberGenerator;
    
    @InjectMocks AccountService accountService;
    
    @Test
    void createAccount_ShouldGenerateUniqueAccountNumber() {
        // Given
        when(numberGenerator.generateAccountNumber()).thenReturn("CITI000000000001");
        when(accountRepository.existsByAuthUserIdAndAccountType(1L, AccountType.SAVINGS))
            .thenReturn(false);
        when(accountRepository.findByAuthUserId(1L)).thenReturn(List.of());
        
        // When
        Account account = accountService.createAccount(
            AccountType.SAVINGS, BigDecimal.valueOf(5000), 1L, "user@test.com");
        
        // Then
        assertEquals("CITI000000000001", account.getAccountNumber());
        assertEquals(BigDecimal.valueOf(5000), account.getBalance());
        verify(statementRepository).save(argThat(stmt -> 
            stmt.getTransactionType() == TransactionType.CREDIT &&
            stmt.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0
        ));
    }
    
    @Test
    void debit_ShouldThrowInsufficientBalance_WhenBalanceTooLow() {
        // Given
        Account account = Account.builder()
            .accountNumber("CITI000000000001")
            .authUserId(1L)
            .balance(BigDecimal.valueOf(1000))
            .accountType(AccountType.SAVINGS)
            .build();
        when(accountRepository.findByAccountNumberWithLock("CITI000000000001"))
            .thenReturn(Optional.of(account));
        
        // When/Then
        assertThrows(InsufficientBalanceException.class, () ->
            accountService.debit("CITI000000000001", BigDecimal.valueOf(2000), 
                "TXN_001", 1L, "user@test.com"));
    }
    
    @Test
    void debit_ShouldEnforceMinimumBalance_ForSavings() {
        // Given: balance 2000, min savings 1000, debit 1500 → would leave 500 < 1000
        Account account = Account.builder()
            .accountNumber("CITI000000000001")
            .authUserId(1L)
            .balance(BigDecimal.valueOf(2000))
            .accountType(AccountType.SAVINGS)
            .build();
        when(accountRepository.findByAccountNumberWithLock("CITI000000000001"))
            .thenReturn(Optional.of(account));
        
        // When/Then
        assertThrows(MinimumBalanceViolationException.class, () ->
            accountService.debit("CITI000000000001", BigDecimal.valueOf(1500), 
                "TXN_001", 1L, "user@test.com"));
    }
    
    @Test
    void credit_ShouldBeIdempotent_OnDuplicateTxnRef() {
        // Given
        when(statementRepository.existsByTxnRef("TXN_001_CREDIT")).thenReturn(true);
        
        // When
        accountService.credit("CITI000000000001", BigDecimal.valueOf(1000), 
            "TXN_001_CREDIT", 1L, "user@test.com");
        
        // Then - no exception, no save called
        verify(accountRepository, never()).save(any());
        verify(statementRepository, never()).save(any());
    }
}
```

#### TransactionService Tests
```java
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    
    @Mock AccountFeignClient accountFeignClient;
    @Mock TransactionRepository transactionRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock TransactionLimitValidator limitValidator;
    @Mock TxnRefGenerator txnRefGenerator;
    @Mock ObjectMapper objectMapper;
    
    @InjectMocks TransactionService transactionService;
    
    @Test
    void transfer_ShouldReturnExistingTxnRef_OnDuplicateIdempotencyKey() {
        // Given
        IdempotencyKey existing = new IdempotencyKey();
        existing.setIdempotencyKey("IDEMP_001");
        existing.setTxnRef("TXN_EXISTING");
        when(idempotencyKeyRepository.findByIdempotencyKey("IDEMP_001"))
            .thenReturn(Optional.of(existing));
        
        // When
        ApiResponse<String> response = transactionService.transfer(
            new TransferRequest("FROM", "TO", BigDecimal.valueOf(1000), TransactionType.TRANSFER),
            "IDEMP_001");
        
        // Then
        assertTrue(response.isSuccess());
        assertEquals("TXN_EXISTING", response.getData());
    }
    
    @Test
    void transfer_ShouldValidateDailyLimit() {
        // Given
        when(transactionRepository.getTodayTotal(any(), any(), any()))
            .thenReturn(BigDecimal.valueOf(490000)); // Near limit
        when(limitValidator.validate(...)).thenThrow(new DailyLimitExceededException("..."));
        
        // When/Then
        assertThrows(DailyLimitExceededException.class, () -> 
            transactionService.transfer(validRequest(), "IDEMP_NEW"));
    }
}
```

#### DebitConsumer Tests (Kafka Consumer)
```java
@SpringBootTest
@EmbeddedKafka(topics = "transfer-requested-topic")
class DebitConsumerIntegrationTest {
    
    @Autowired DebitConsumer debitConsumer;
    @Autowired AccountService accountService;
    @Autowired AccountOutboxRepository outboxRepository;
    @Autowired ObjectMapper objectMapper;
    
    @Test
    void onTransfer_ShouldDebitAccount_AndPublishDebitSuccess() {
        // Given: account with balance
        Account account = Account.builder()
            .accountNumber("CITI000000000001")
            .authUserId(1L)
            .balance(BigDecimal.valueOf(10000))
            .accountType(AccountType.SAVINGS)
            .build();
        accountRepository.save(account);
        
        TransactionRequestedEvent event = new TransactionRequestedEvent(
            "TXN_001", "CITI000000000001", "CITI000000000002", 
            BigDecimal.valueOf(3000), 1L);
        String payload = objectMapper.writeValueAsString(event);
        
        // When: consume from Kafka
        debitConsumer.onTransfer(payload);
        
        // Then: balance reduced, debit success event in outbox
        Account updated = accountRepository.findByAccountNumber("CITI000000000001").get();
        assertEquals(BigDecimal.valueOf(7000), updated.getBalance());
        
        List<AccountOutboxEvent> outbox = outboxRepository.findByStatus(OutboxStatus.PENDING);
        assertTrue(outbox.stream().anyMatch(e -> e.getTopic().equals("debit-success-topic")));
    }
}
```

---

## 3. Integration Testing (Testcontainers)

### 3.1 Infrastructure
```java
@Testcontainers
@SpringBootTest
class AccountServiceIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("citicore_account")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    void createAccount_ShouldPersistToDatabase() {
        // Given
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);
        request.setInitialDeposit(BigDecimal.valueOf(5000));
        
        // When
        ResponseEntity<ApiResponse<AccountResponse>> response = 
            accountController.createAccount(request);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        
        // Verify in DB
        Account saved = accountRepository.findByAccountNumber(
            response.getBody().getData().getAccountNumber()).get();
        assertEquals(BigDecimal.valueOf(5000), saved.getBalance());
    }
}
```

### 3.2 Critical Integration Scenarios

| Scenario | Test Class | Verification |
|----------|------------|--------------|
| Full transfer saga success | `TransferSagaIntegrationTest` | INITIATED → DEBIT_SUCCESS → CREDIT_SUCCESS |
| Transfer with insufficient balance | `TransferSagaIntegrationTest` | INITIATED → DEBIT_FAILED → FAILED |
| Transfer with credit failure (reversal) | `TransferSagaIntegrationTest` | INITIATED → DEBIT_SUCCESS → CREDIT_FAILED → REVERSED |
| Concurrent transfers same account | `ConcurrencyIntegrationTest` | No lost updates, correct final balance |
| Duplicate idempotency key | `IdempotencyIntegrationTest` | Returns existing txnRef |
| Kafka broker restart mid-saga | `ResilienceIntegrationTest` | Saga recovers, no lost events |
| Database deadlock handling | `ConcurrencyIntegrationTest` | Retry succeeds |

---

## 4. Contract Testing (Spring Cloud Contract)

### 4.1 Producer Contracts (Account Service)
```groovy
// contracts/account-service/validate-transfer.groovy
Contract.make {
    request {
        method 'GET'
        url '/accounts/validate-transfer'
        queryParameters {
            parameter 'accNo': 'CITI000000000001'
            parameter 'amount': '1000.00'
        }
        headers {
            header 'Authorization': 'Bearer valid-token'
        }
    }
    response {
        status 200
    }
}
```

### 4.2 Consumer Contracts (Transaction Service)
```java
// Auto-generated stubs used in TransactionServiceTest
@SpringBootTest
@AutoConfigureStubRunner(ids = "com.citicore:account-service:+:stubs:8083")
class TransactionServiceContractTest {
    
    @Autowired AccountFeignClient accountFeignClient;
    
    @Test
    void validateTransfer_ShouldCallAccountService() {
        // Contract ensures this call matches producer contract
        accountFeignClient.validateTransfer("CITI000000000001", BigDecimal.valueOf(1000));
    }
}
```

---

## 5. Security Testing

### 5.1 SAST (Static Analysis)
```yaml
# GitHub Actions - CodeQL
- name: Run CodeQL
  uses: github/codeql-action/init@v2
  with:
    languages: java
    queries: security-extended,security-and-quality
```

### 5.2 DAST (Dynamic Analysis)
```yaml
# OWASP ZAP Scan
- name: ZAP Scan
  uses: zaproxy/action-full-scan@v0.5.0
  with:
    target: 'http://localhost:8080'
    rules: 'Strict'
```

### 5.3 Dependency Scanning
```yaml
# OWASP Dependency Check
- name: Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: 'CitiCore'
    format: 'HTML'
    out: 'reports'
```

### 5.4 Penetration Testing Checklist
- [ ] JWT algorithm confusion
- [ ] JWT none algorithm
- [ ] JWT key confusion
- [ ] Broken object level authorization (BOLA)
- [ ] Broken function level authorization (BFLA)
- [ ] Mass assignment
- [ ] Injection (SQL, NoSQL, LDAP)
- [ ] XSS in error messages
- [ ] Rate limiting bypass
- [ ] IDOR on account endpoints
- [ ] Refresh token replay

---

## 6. Performance Testing

### 6.1 Load Test Scenarios (k6/Gatling)

```javascript
// k6 script: transfer-load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '2m', target: 50 },   // Ramp up
        { duration: '5m', target: 100 },  // Steady state
        { duration: '2m', target: 200 },  // Stress
        { duration: '5m', target: 200 },  // Sustained stress
        { duration: '2m', target: 0 },    // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function() {
    const token = getAuthToken();
    const payload = JSON.stringify({
        fromAccount: `CITI${String(Math.floor(Math.random() * 10000)).padStart(12, '0')}`,
        toAccount: `CITI${String(Math.floor(Math.random() * 10000)).padStart(12, '0')}`,
        amount: Math.floor(Math.random() * 10000) + 100,
        type: 'TRANSFER'
    });
    
    const headers = {
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': crypto.randomUUID(),
        'Content-Type': 'application/json'
    };
    
    const res = http.post('http://localhost:8080/api/v1/transaction/transfer', payload, { headers });
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(1);
}
```

### 6.2 Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Transfer initiation (p99) | < 500ms | k6/Gatling |
| Balance query (p99) | < 50ms | k6/Gatling |
| Statement query (p99) | < 200ms | k6/Gatling |
| Throughput (transfers/sec) | > 100 | k6/Gatling |
| Error rate | < 0.1% | k6/Gatling |
| Kafka consumer lag | < 100 | Prometheus |
| DB connection pool usage | < 80% | Prometheus |

---

## 7. Chaos Engineering

### 7.1 Chaos Experiments (Chaos Mesh/Litmus)

| Experiment | Hypothesis | Blast Radius |
|------------|------------|--------------|
| Kill MySQL primary | Replica promotes, writes resume | Account service |
| Kill Kafka broker | ISR rebalances, no message loss | All services |
| Network partition (account ↔ transaction) | Saga pauses, recovers on heal | Transfer flow |
| Redis outage | Cache misses, DB handles load | Balance queries |
| High CPU on account service | HPA scales, latency degrades gracefully | Account service |
| Disk full on MySQL | Alerts fire, no data corruption | Database |

### 7.2 Experiment Template
```yaml
# chaos-experiment-transfer-failure.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: kill-account-service
spec:
  action: pod-failure
  mode: one
  duration: "30s"
  selector:
    namespaces:
      - default
    labelSelectors:
      app: account-service
```

---

## 8. Financial Invariant Testing

### 8.1 Core Invariants (Must Pass Every Build)

```java
@SpringBootTest
class FinancialInvariantsTest {
    
    @Autowired AccountRepository accountRepository;
    @Autowired AccountStatementRepository statementRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired JournalEntryRepository journalRepository; // After ledger implementation
    
    @Test
    void invariant_AccountBalanceEqualsStatementSum() {
        // For every account: balance == sum(credits) - sum(debits)
        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            BigDecimal computed = statementRepository
                .findByAccountNumber(account.getAccountNumber())
                .stream()
                .reduce(BigDecimal.ZERO, (sum, stmt) -> 
                    stmt.getTransactionType() == TransactionType.CREDIT 
                        ? sum.add(stmt.getAmount())
                        : sum.subtract(stmt.getAmount()), BigDecimal::add);
            
            assertEquals(0, account.getBalance().compareTo(computed),
                "Account " + account.getAccountNumber() + " balance mismatch");
        }
    }
    
    @Test
    void invariant_JournalEntriesAlwaysBalance() {
        // After ledger implementation
        List<JournalEntry> entries = journalRepository.findAll();
        for (JournalEntry entry : entries) {
            BigDecimal totalDebit = entry.getLines().stream()
                .map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCredit = entry.getLines().stream()
                .map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            
            assertEquals(0, totalDebit.compareTo(totalCredit),
                "Journal " + entry.getTxnRef() + " does not balance");
        }
    }
    
    @Test
    void invariant_NoNegativeBalances() {
        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            assertTrue(account.getBalance().signum() >= 0,
                "Account " + account.getAccountNumber() + " has negative balance");
        }
    }
    
    @Test
    void invariant_MinimumBalanceMaintained() {
        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            BigDecimal min = account.getAccountType() == AccountType.SAVINGS 
                ? BigDecimal.valueOf(1000) : BigDecimal.valueOf(5000);
            assertTrue(account.getBalance().compareTo(min) >= 0,
                "Account " + account.getAccountNumber() + " below minimum balance");
        }
    }
    
    @Test
    void invariant_TransferConservation() {
        // Money in = Money out for every transfer
        List<Transaction> transfers = transactionRepository.findByType(TransactionType.TRANSFER);
        for (Transaction txn : transfers) {
            // Debit from sender = Credit to receiver (when both success)
            if (txn.getStatus() == TransactionStatus.CREDIT_SUCCESS) {
                BigDecimal debitSum = statementRepository
                    .findByAccountNumberAndTxnRefEndsWith(txn.getFromAccount(), "_DEBIT")
                    .stream().map(AccountStatement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal creditSum = statementRepository
                    .findByAccountNumberAndTxnRefEndsWith(txn.getToAccount(), "_CREDIT")
                    .stream().map(AccountStatement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                
                assertEquals(0, debitSum.compareTo(creditSum),
                    "Transfer " + txn.getTxnRef() + " debit != credit");
            }
        }
    }
}
```

---

## 9. Test Data Management

### 9.1 Test Data Builders
```java
public class AccountTestDataBuilder {
    private String accountNumber = "CITI" + UUID.randomUUID().toString().substring(0, 12);
    private Long authUserId = 1L;
    private AccountType accountType = AccountType.SAVINGS;
    private BigDecimal balance = BigDecimal.valueOf(10000);
    private AccountStatus status = AccountStatus.ACTIVE;
    
    public AccountTestDataBuilder withAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber; return this;
    }
    public AccountTestDataBuilder withBalance(BigDecimal balance) {
        this.balance = balance; return this;
    }
    public AccountTestDataBuilder withType(AccountType type) {
        this.accountType = type; return this;
    }
    public Account build() {
        return Account.builder()
            .accountNumber(accountNumber)
            .authUserId(authUserId)
            .accountType(accountType)
            .balance(balance)
            .status(status)
            .build();
    }
}
```

### 9.2 Database Seeding
```sql
-- test-data.sql (run before integration tests)
INSERT INTO users (id, email, password, email_verified, status) VALUES 
(1, 'test@citicore.com', '$2a$10$...', true, 'ACTIVE'),
(2, 'test2@citicore.com', '$2a$10$...', true, 'ACTIVE');

INSERT INTO roles (id, name) VALUES (1, 'ROLE_CUSTOMER'), (2, 'ROLE_ADMIN');

INSERT INTO user_roles (user_id, role_id) VALUES (1, 1), (2, 1);

INSERT INTO accounts (account_number, auth_user_id, account_type, balance, status, is_default) VALUES
('CITI000000000001', 1, 'SAVINGS', 10000.00, 'ACTIVE', true),
('CITI000000000002', 2, 'CURRENT', 50000.00, 'ACTIVE', true);
```

---

## 10. CI/CD Test Gates

```yaml
# .github/workflows/test.yml
jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw test -Dtest=*UnitTest
      - run: ./mvnw jacoco:report
      - name: Check Coverage
        run: |
          COVERAGE=$(cat target/site/jacoco/index.html | grep -oP 'Total.*?(\d+)%' | head -1)
          if [ ${COVERAGE%\%} -lt 80 ]; then exit 1; fi
  
  integration-test:
    runs-on: ubuntu-latest
    services:
      mysql: { image: mysql:8.0, env: { MYSQL_ROOT_PASSWORD: test }, ports: [3306:3306] }
      kafka: { image: apache/kafka:3.8.0, ports: [29092:29092] }
      redis: { image: redis:7-alpine, ports: [6379:6379] }
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw verify -Dtest=*IntegrationTest
  
  contract-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./mvnw test -Dtest=*ContractTest
  
  security-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: CodeQL
        uses: github/codeql-action/init@v2
      - name: Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
  
  performance-test:
    runs-on: ubuntu-latest
    if: github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'
    steps:
      - uses: actions/checkout@v4
      - name: Run k6
        run: k6 run performance/transfer-load-test.js
  
  chaos-test:
    runs-on: ubuntu-latest
    if: github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'
    steps:
      - name: Run Chaos Experiments
        run: kubectl apply -f chaos/experiments/
```

---

## 11. Test Reporting & Metrics

### 11.1 Required Reports
| Report | Tool | Frequency |
|--------|------|-----------|
| Unit Test Coverage | JaCoCo | Every PR |
| Integration Test Results | Surefire/Failsafe | Every PR |
| Contract Test Results | Spring Cloud Contract | Every PR |
| Security Scan Results | CodeQL, Dependency Check | Every PR |
| Performance Benchmarks | k6/Gatling | Nightly |
| Chaos Experiment Results | Chaos Mesh | Weekly |
| Financial Invariant Checks | Custom | Every Build |

### 11.2 Quality Gates
| Gate | Threshold | Blocking |
|------|-----------|----------|
| Unit coverage | ≥ 80% | Yes |
| Integration pass rate | 100% | Yes |
| Contract compatibility | 100% | Yes |
| Critical/High vulns | 0 | Yes |
| Performance regression | < 10% slower | Warning |
| Financial invariants | 100% pass | Yes |

---

## 12. Test Environment Strategy

| Environment | Purpose | Data | Refresh |
|-------------|---------|------|---------|
| Local | Developer testing | Synthetic | On demand |
| CI | Automated testing | Synthetic | Per build |
| Staging | Integration, performance | Production-like (masked) | Daily |
| Chaos | Resilience testing | Subset | Per experiment |
| Production | Canary, monitoring | Real | N/A |

---

## 13. Test Documentation

Each test class must include:
```java
/**
 * Test: {Feature} - {Scenario}
 * 
 * Given: {Preconditions}
 * When: {Action}
 * Then: {Expected outcome}
 * 
 * Financial Invariant: {Which invariant this protects}
 * Priority: P0/P1/P2
 * 
 * @see BUG-XXX / SEC-XXX / GAP-XXX
 */
```

---

## 14. Testing Checklist for New Features

- [ ] Unit tests for all service methods (≥90% coverage)
- [ ] Integration tests for DB/Kafka/Feign interactions
- [ ] Contract tests for API changes
- [ ] Financial invariant tests (balance conservation, min balance, etc.)
- [ ] Concurrency tests (pessimistic locking, idempotency)
- [ ] Failure scenario tests (insufficient balance, network timeout, Kafka down)
- [ ] Security tests (auth, authorization, validation)
- [ ] Performance benchmarks (if user-facing)
- [ ] Chaos experiment (if critical path)
- [ ] Documentation updated (API docs, runbooks)
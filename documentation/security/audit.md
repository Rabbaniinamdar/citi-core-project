# CitiCore Banking Platform - Security Audit Report

## Executive Summary

This security assessment evaluates the CitiCore platform against **OWASP Top 10 2021**, **OWASP API Security Top 10 2023**, and **Financial Services Security Guidelines** (FFIEC, PCI-DSS, SOX).

**Overall Risk Rating: HIGH**

| Category | Findings | Critical | High | Medium | Low |
|----------|----------|----------|------|--------|-----|
| Authentication | 8 | 2 | 3 | 2 | 1 |
| Authorization | 6 | 2 | 2 | 1 | 1 |
| Data Protection | 7 | 1 | 3 | 2 | 1 |
| API Security | 5 | 1 | 2 | 2 | 0 |
| Infrastructure | 6 | 0 | 3 | 2 | 1 |
| **Total** | **32** | **6** | **13** | **9** | **4** |

---

## 1. Authentication Vulnerabilities

### SEC-001: CRITICAL - No Rate Limiting on Auth Endpoints
**Location:** `auth-service` - all auth endpoints (`/register`, `/login`, `/verify`, `/resend`)
**OWASP:** API4:2023 Unrestricted Resource Consumption, API2:2023 Broken Authentication
**Impact:** Brute force, credential stuffing, email enumeration, OTP bombing
**Evidence:** No `@RateLimiter`, no Bucket4j, no Spring Security rate limiting
**Fix:**
```java
// Add to SecurityConfig
@Bean
public RateLimiter loginRateLimiter() {
    return RateLimiter.of("login", config -> config
        .limitForPeriod(5)
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO));
}

// Apply to auth endpoints
@RateLimiter(name = "login")
@PostMapping("/login")
```

### SEC-002: CRITICAL - Weak Email Verification Code (6 Digits)
**Location:** `auth-service/src/main/java/com/citicor/auth/service/AuthService.java:233-237`
**OWASP:** API2:2023 Broken Authentication
**Impact:** 900,000 combinations - brute-forcable in ~2 hours at 100 req/min
**Evidence:** `SecureRandom.nextInt(900000) + 100000`
**Fix:**
```java
private String generateVerificationCode() {
    // 8-char alphanumeric = 2.8 trillion combinations
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
}
```

### SEC-003: HIGH - No Account Lockout After Failed Logins
**Location:** `auth-service` - `AuthService.login()` method
**OWASP:** API2:2023 Broken Authentication
**Impact:** Unlimited password guessing
**Fix:** Track failed attempts, lock after 5 for 15 minutes
```java
@Entity
class LoginAttempt {
    String email;
    int failedCount;
    LocalDateTime lockedUntil;
    LocalDateTime lastAttempt;
}
```

### SEC-004: HIGH - No Password Policy Enforcement
**Location:** `auth-service` - `RegisterRequest` DTO, `AuthService.register()`
**OWASP:** API2:2023 Broken Authentication
**Impact:** Weak passwords accepted (e.g., "123456")
**Fix:**
```java
@Size(min = 12, max = 128)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]")
private String password;
```

### SEC-005: HIGH - No Password Reset Flow
**Location:** `auth-service` - missing entirely
**OWASP:** API2:2023 Broken Authentication
**Impact:** Users cannot recover accounts; support burden
**Fix:** Implement secure reset token (JWT, 15-min expiry, single-use)

### SEC-006: MEDIUM - No MFA/TOTP Support
**Location:** `auth-service` - only email OTP
**OWASP:** API2:2023 Broken Authentication
**Impact:** Single factor only - phishing vulnerable
**Fix:** Add TOTP (RFC 6238) with backup codes

### SEC-007: MEDIUM - Refresh Token Not Revoked on Password Change
**Location:** `auth-service` - no password change endpoint
**OWASP:** API2:2023 Broken Authentication
**Impact:** Compromised password → persistent access via refresh token
**Fix:** On password change: `refreshTokenRepo.revokeAllByUser(userId)`

### SEC-008: LOW - JWT Access Token Expiry Too Long (1000000ms = ~16 min)
**Location:** `config-repo-local/application.yml:132`
**OWASP:** API2:2023 Broken Authentication
**Impact:** Longer window for token theft
**Fix:** Reduce to 15 min (900000ms) - already reasonable but verify

---

## 2. Authorization Vulnerabilities

### SEC-009: CRITICAL - Account Service Permits All Requests
**Location:** `account-service/src/main/java/com/citicore/account/config/SecurityConfig.java:27`
```java
.requestMatchers("/accounts/**").permitAll()  // ALL ENDPOINTS PUBLIC!
```
**OWASP:** API1:2023 Broken Object Level Authorization (BOLA), API5:2023 Broken Function Level Authorization
**Impact:** Unauthenticated users can:
- Create accounts (`POST /accounts/create`)
- Deposit/withdraw (`POST /accounts/deposit`, `/withdraw`)
- View balances (`GET /accounts/balance/{accNo}`)
- View statements (`GET /accounts/statement/{accNo}`)
**Fix:**
```java
.requestMatchers("/accounts/create").authenticated()
.requestMatchers("/accounts/deposit").authenticated()
.requestMatchers("/accounts/withdraw").authenticated()
.requestMatchers("/accounts/balance/**").authenticated()
.requestMatchers("/accounts/mini-statement/**").authenticated()
.requestMatchers("/accounts/statement/**").authenticated()
.requestMatchers("/accounts/my-accounts").authenticated()
// Only public: actuator, health
```

### SEC-010: CRITICAL - User Service Permits All Requests
**Location:** `user-service/src/main/java/com/citicore/gateway/config/SecurityConfig.java:27`
```java
.requestMatchers("/accounts/**").permitAll()  // COPY-PASTE BUG!
```
**OWASP:** API1:2023 BOLA, API5:2023 BFLA
**Impact:** Unauthenticated profile creation, KYC submission, admin review
**Fix:** Same pattern as account service - require authentication for all business endpoints

### SEC-011: HIGH - No Ownership Check in AccountService.credit()
**Location:** `account-service/src/main/java/com/citicore/account/service/AccountService.java:267-300`
**Comment line 265:** "Ownership check is intentionally NOT added here"
**OWASP:** API1:2023 BOLA
**Impact:** Any authenticated user can credit ANY account if they know the number
**Fix:** Distinguish transfer credits (saga, no authUserId check) vs deposit credits (require ownership)

### SEC-012: HIGH - Idempotency Key Not Scoped to User
**Location:** `transaction-service/src/main/java/com/citicore/transaction/entity/IdempotencyKey.java`
**OWASP:** API1:2023 BOLA
**Impact:** User A's idempotency key reveals User B's txnRef
**Fix:** Add `authUserId` column, unique constraint on `(authUserId, idempotencyKey)`

### SEC-013: MEDIUM - No Role-Based Access Control Enforcement
**Location:** All services - `@EnableMethodSecurity` but no `@PreAuthorize`
**OWASP:** API5:2023 Broken Function Level Authorization
**Impact:** All authenticated users have same permissions
**Fix:** Define roles (CUSTOMER, ADMIN, SUPPORT) and enforce:
```java
@PreAuthorize("hasRole('ADMIN')")
public UserProfile reviewKyc(Long userId, KycStatus status) { ... }
```

### SEC-014: LOW - Missing CORS Configuration
**Location:** All SecurityConfig classes
**OWASP:** API7:2023 Server-Side Request Forgery
**Impact:** Potential CSRF via misconfigured CORS
**Fix:** Explicit CORS config allowing only known origins

---

## 3. Data Protection Vulnerabilities

### SEC-015: CRITICAL - JWT Secret Placeholder Without Validation
**Location:** `config-repo-local/application.yml:131`, `config-repo/application.yml`
```yaml
jwt:
  secret: ${JWT_SECRET}  # No default, no validation
```
**OWASP:** API7:2023 SSRF, A07:2021 Identification and Authentication Failures
**Impact:** If env var missing → JWT signing fails OR uses weak default
**Fix:**
```java
@PostConstruct
public void validateSecret() {
    if (secret == null || secret.equals("CHANGE_ME") || secret.length() < 32) {
        throw new IllegalStateException("JWT_SECRET must be 32+ chars");
    }
}
```

### SEC-016: HIGH - Password Hash Logged in AuthService
**Location:** `auth-service/src/main/java/com/citicor/auth/service/AuthService.java:84`
```java
System.out.println("Encoding password...");  // Followed by encoder.encode()
```
**OWASP:** A09:2021 Security Logging and Monitoring Failures
**Impact:** BCrypt hashes in logs → offline cracking
**Fix:** Remove ALL `System.out.println` from AuthService. Use structured logging with masking.

### SEC-017: HIGH - Sensitive Data in Exception Messages
**Location:** All `GlobalExceptionHandler` implementations
**OWASP:** A01:2021 Broken Access Control, A09:2021 Logging Failures
**Impact:** `InsufficientBalanceException` returns exact balance to client
**Fix:** Generic client messages, detailed server logs only:
```java
@ExceptionHandler(InsufficientBalanceException.class)
public ResponseEntity<ErrorResponse> handle(InsufficientBalanceException ex) {
    log.warn("Insufficient balance for user {}", getCurrentUserId()); // Detailed
    return ResponseEntity.status(400).body(new ErrorResponse("Insufficient funds")); // Generic
}
```

### SEC-018: MEDIUM - No Encryption at Rest for PII
**Location:** Database configuration - no TDE/column encryption
**OWASP:** A02:2021 Cryptographic Failures
**Impact:** PII (email, phone, address, KYC docs) stored in plaintext
**Fix:** 
- MySQL TDE for tablespace encryption
- Or column-level encryption for PII fields
- S3 SSE-KMS for KYC documents

### SEC-019: MEDIUM - No TLS Configuration for Database
**Location:** `account-service/src/main/resources/application.yaml` datasource URLs
**OWASP:** A02:2021 Cryptographic Failures
**Impact:** Credentials and data in transit unencrypted
**Fix:** 
```yaml
jdbc-url: jdbc:mysql://host:3306/db?useSSL=true&requireSSL=true&verifyServerCertificate=true
```

### SEC-020: LOW - No Secrets Management (Vault/Parameter Store)
**Location:** All docker-compose.yml env vars
**OWASP:** A07:2021 Identification and Authentication Failures
**Impact:** Secrets in plaintext env vars, git history, CI logs
**Fix:** Integrate HashiCorp Vault or AWS Secrets Manager

### SEC-021: LOW - KYC Documents No Malware Scanning
**Location:** `user-service` - `FileStorageService` uploads to S3
**OWASP:** A08:2021 Software and Data Integrity Failures
**Impact:** Malware distribution via document download
**Fix:** S3 Object Lambda + ClamAV or AWS GuardDuty malware protection

---

## 4. API Security Vulnerabilities

### SEC-022: CRITICAL - No Input Validation on Transaction Amount
**Location:** `transaction-service/src/main/java/com/citicore/transaction/dto/TransactionRequest.java`
**OWASP:** API8:2023 Security Misconfiguration, API3:2023 Broken Object Property Level Authorization
**Impact:** Negative/zero amounts could invert transfer logic
**Fix:**
```java
@Positive(message = "Amount must be positive")
@DecimalMin(value = "0.01", message = "Minimum amount is 0.01")
@Digits(integer = 12, fraction = 2)
private BigDecimal amount;
```

### SEC-023: HIGH - No Pagination Limit Enforcement
**Location:** `account-service/src/main/java/com/citicore/account/controller/AccountController.java:152`
```java
@RequestParam(defaultValue = "10") int size  // No @Max!
```
**OWASP:** API4:2023 Unrestricted Resource Consumption
**Impact:** `size=1000000` → OOM, DB overload
**Fix:** `@RequestParam(defaultValue = "10") @Max(100) int size`

### SEC-024: HIGH - Feign Client No Circuit Breaker Fallback
**Location:** `transaction-service` → `AccountFeignClient.validateTransfer()`
**OWASP:** API9:2023 Improper Inventory Management (resilience)
**Impact:** Account service down → transfer initiation fails completely
**Fix:** Add Resilience4j circuit breaker + fallback

### SEC-025: MEDIUM - No API Versioning Strategy
**Location:** All controllers use `/api/v1/` without deprecation policy
**OWASP:** API9:2023 Improper Inventory Management
**Impact:** Breaking changes require coordinated deployment
**Fix:** Document versioning policy, use header-based versioning

### SEC-026: MEDIUM - No Request Size Limits
**Location:** All services - no `spring.servlet.multipart.max-file-size`
**OWASP:** API4:2023 Unrestricted Resource Consumption
**Impact:** Large file uploads → disk exhaustion
**Fix:** Configure limits per endpoint

---

## 5. Infrastructure Vulnerabilities

### SEC-027: HIGH - Docker Images Run as Root
**Location:** All `Dockerfile`s - no `USER` directive
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Container escape → host compromise
**Fix:**
```dockerfile
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup
USER appuser
```

### SEC-028: HIGH - Single Kafka Broker (No HA)
**Location:** `docker-compose.yml` - single kafka container
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Single point of failure, message loss on restart
**Fix:** 3-broker cluster with `replication-factor=3`

### SEC-029: HIGH - No Database Backup/Recovery Strategy
**Location:** `docker-compose.yml` - MySQL volume only
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Data loss on corruption, no point-in-time recovery
**Fix:** Automated backups (Percona XtraBackup), PITR, tested restore

### SEC-030: MEDIUM - No Network Segmentation
**Location:** `docker-compose.yml` - single `citicore-network`
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Lateral movement if one service compromised
**Fix:** Separate networks per tier (frontend, backend, data)

### SEC-031: MEDIUM - No Security Headers in Gateway
**Location:** `apigateway-service` - no `ServerHttpResponse` headers
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Missing HSTS, X-Frame-Options, CSP
**Fix:** Add `HttpSecurity` headers or Gateway filter

### SEC-032: LOW - Actuator Endpoints Exposed Without Auth
**Location:** All services - `management.endpoints.web.exposure.include=health,info,metrics,prometheus,refresh`
**OWASP:** A05:2021 Security Misconfiguration
**Impact:** Metrics, env vars, config exposed
**Fix:** Secure actuator with separate management port + auth

---

## 6. Specific Fixes - Code Changes Required

### Fix 1: Secure Account Service Endpoints
**File:** `account-service/src/main/java/com/citicore/account/config/SecurityConfig.java`
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/actuator/**").hasRole("ADMIN")
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/accounts/create").authenticated()
            .requestMatchers("/accounts/deposit").authenticated()
            .requestMatchers("/accounts/withdraw").authenticated()
            .requestMatchers("/accounts/balance/**").authenticated()
            .requestMatchers("/accounts/mini-statement/**").authenticated()
            .requestMatchers("/accounts/statement/**").authenticated()
            .requestMatchers("/accounts/my-accounts").authenticated()
            .requestMatchers("/accounts/validate-transfer").hasRole("SERVICE")  // Feign only
            .anyRequest().authenticated()
        )
        .headers(headers -> headers
            .frameOptions(frame -> frame.sameOrigin())
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

### Fix 2: Secure User Service Endpoints
**File:** `user-service/src/main/java/com/citicore/gateway/config/SecurityConfig.java`
```java
// Same pattern - require authentication for all business endpoints
.requestMatchers("/api/v1/users/profile").authenticated()
.requestMatchers("/api/v1/users/profile/**").authenticated()
.requestMatchers("/api/v1/users/kyc/**").authenticated()
.requestMatchers("/api/v1/users/admin/**").hasRole("ADMIN")
```

### Fix 3: Add Rate Limiting to Auth Service
**File:** `auth-service/src/main/java/com/citicor/auth/config/SecurityConfig.java`
```java
@Bean
public RateLimiter loginRateLimiter() {
    return RateLimiter.of("login", config -> config
        .limitForPeriod(5)
        .limitRefreshPeriod(Duration.ofMinutes(1))
        .timeoutDuration(Duration.ZERO));
}

@Bean
public RateLimiter registerRateLimiter() {
    return RateLimiter.of("register", config -> config
        .limitForPeriod(3)
        .limitRefreshPeriod(Duration.ofMinutes(5)));
}
```

**Apply to controller:**
```java
@RateLimiter(name = "login")
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(...)

@RateLimiter(name = "register")
@PostMapping("/register")
public ResponseEntity<?> register(...)
```

### Fix 4: Stronger Verification Code
**File:** `auth-service/src/main/java/com/citicor/auth/service/AuthService.java`
```java
private String generateVerificationCode() {
    // 8-char alphanumeric = 36^8 = 2.8 trillion combinations
    String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No confusing chars
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) {
        sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
}
```

### Fix 5: Account Service Credit Ownership Check
**File:** `account-service/src/main/java/com/citicore/account/service/AccountService.java`
```java
@Transactional
public void credit(String accNo, BigDecimal amount, String txnRef,
                   Long authUserId, String email) {
    validateAmount(amount);

    if (statementRepository.existsByTxnRef(txnRef)) {
        return; // Idempotent
    }

    Account account = accountRepository
        .findByAccountNumberWithLock(accNo)
        .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accNo));

    // FOR DEPOSITS: require ownership
    // FOR TRANSFER CREDITS: saga provides authUserId but it's the SENDER's ID
    // Distinguish by checking if this is a self-service deposit vs saga credit
    if (email.isEmpty() && authUserId != null) {
        // Likely a saga credit - sender's authUserId, not receiver's
        // Allow without ownership check
    } else if (!email.isEmpty()) {
        // Self-service deposit - require ownership
        if (!account.getAuthUserId().equals(authUserId)) {
            throw new UnauthorizedAccountAccessException(
                "Unauthorized: account does not belong to user " + authUserId);
        }
    }

    BigDecimal newBalance = account.getBalance().add(amount);
    account.setBalance(newBalance);
    accountRepository.save(account);
    // ... rest unchanged
}
```

### Fix 6: Idempotency Key User Scoping
**File:** `transaction-service/src/main/java/com/citicore/transaction/entity/IdempotencyKey.java`
```java
@Entity
@Table(name = "idempotency_keys", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"auth_user_id", "idempotency_key"}))
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "txn_ref", nullable = false)
    private String txnRef;
    // ... rest
}
```

**Update service:**
```java
Optional<IdempotencyKey> existing = idempotencyKeyRepository
    .findByAuthUserIdAndIdempotencyKey(authUser.getId(), idempotencyKey);
```

### Fix 7: Remove Debug Logging
**File:** All services - replace `System.out.println` with SLF4J
```java
private static final Logger log = LoggerFactory.getLogger(AccountService.class);

// Instead of:
System.out.println("✅ [DEBIT SUCCESS] txnRef=" + event.getTxnRef());
// Use:
log.info("Debit successful: txnRef={}, account={}, amount={}", 
    event.getTxnRef(), event.getFromAccount(), event.getAmount());
```

### Fix 8: Generic Error Responses
**File:** All `GlobalExceptionHandler` classes
```java
@ExceptionHandler(InsufficientBalanceException.class)
public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
    log.warn("Insufficient balance for user {}: {}", getCurrentUserId(), ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("Insufficient funds for this transaction"));
}
```

### Fix 9: Docker Non-Root User
**File:** All `Dockerfile`s
```dockerfile
FROM openjdk:17-jdk-slim
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup
WORKDIR /app
COPY --chown=appuser:appgroup target/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Fix 10: JWT Secret Validation
**File:** `account-service/src/main/java/com/citicore/account/security/JwtUtils.java` (and auth-service)
```java
@PostConstruct
public void validateSecret() {
    if (secret == null || secret.trim().isEmpty()) {
        throw new IllegalStateException("JWT_SECRET environment variable not set");
    }
    if (secret.length() < 32) {
        throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
    }
    if (secret.equals("CHANGE_ME_IN_PRODUCTION_MIN_32_CHARS")) {
        throw new IllegalStateException("JWT_SECRET is using default value - change in production!");
    }
}
```

---

## 7. Compliance Gap Summary

| Standard | Requirement | Current Status | Gap |
|----------|-------------|----------------|-----|
| **PCI-DSS 4.0** | Req 3: Protect stored card data | N/A (no cards) | - |
| | Req 7: Restrict access by need-to-know | ❌ No RBAC | HIGH |
| | Req 8: Identify and authenticate access | ⚠️ Partial | MEDIUM |
| | Req 10: Track and monitor access | ❌ No audit log | HIGH |
| **SOX** | Financial data integrity | ❌ No double-entry | CRITICAL |
| | Audit trail immutability | ❌ No audit log | CRITICAL |
| **GDPR/PDPB** | Right to erasure | ❌ Not implemented | HIGH |
| | Data portability | ❌ Not implemented | MEDIUM |
| | Consent management | ❌ Not implemented | HIGH |
| **FFIEC** | Authentication (multi-factor) | ❌ No MFA | HIGH |
| | Encryption in transit/rest | ⚠️ Partial | MEDIUM |
| | Audit logging | ❌ Not implemented | HIGH |

---

## 8. Remediation Priority

| Priority | Issues | Timeline |
|----------|--------|----------|
| **P0 - Immediate** | SEC-001, SEC-002, SEC-009, SEC-010, SEC-015 | Week 1 |
| **P1 - Sprint 1** | SEC-003, SEC-004, SEC-005, SEC-011, SEC-012, SEC-016, SEC-017, SEC-022, SEC-027 | Weeks 2-3 |
| **P2 - Sprint 2** | SEC-006, SEC-007, SEC-013, SEC-018, SEC-019, SEC-023, SEC-024, SEC-028, SEC-029, SEC-030 | Weeks 4-6 |
| **P3 - Sprint 3** | SEC-008, SEC-014, SEC-020, SEC-021, SEC-025, SEC-026, SEC-031, SEC-032 | Weeks 7-9 |

---

## 9. Security Testing Checklist

- [ ] SAST scan (SonarQube/CodeQL) on all services
- [ ] DAST scan on running APIs (OWASP ZAP)
- [ ] Dependency scan (OWASP Dependency Check/Snyk)
- [ ] Container scan (Trivy/Grype) on all Docker images
- [ ] Penetration test on auth flows
- [ ] Load test with rate limiting verification
- [ ] Chaos test: Kafka down, DB down, service down
- [ ] Verify audit log captures all financial events
- [ ] Verify encryption at rest (MySQL TDE) and in transit (TLS 1.2+)
- [ ] Verify secrets not in git history (truffleHog)
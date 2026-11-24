# ADR-009: MFA Methods — Email OTP Primary

## Status
✅ **APPROVED** (2026-11-23) — *Updated from TOTP to Email OTP*

## Context

The CitiCore platform requires Multi-Factor Authentication (MFA) for enhanced security, particularly for staff roles (KYC_OFFICER, KYC_MANAGER, ADMIN, etc.) and high-value customer transactions.

**Requirements:**
- Strong second factor for staff roles (regulatory requirement)
- Customer-friendly method for broader accessibility
- Support for account recovery if MFA method lost
- Compliance with RBI/PCI-DSS guidelines
- Simplified user experience — no authenticator app required

## Decision

**Use Email OTP as the primary (and only) MFA method.**

### MFA Methods

| Method | Role | Type | Description |
|--------|------|------|-------------|
| **Email OTP** | Primary | Email | 6-digit numeric code, 5-min expiry, 3 max attempts |

### Enrollment Flow

```mermaid
sequenceDiagram
    participant User
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant DB as Database
    participant NOTIF as Notification Service
    
    User->>GW: POST /api/v1/auth/mfa/enable
    GW->>AUTH: Forward (JWT validated)
    AUTH->>DB: Update user: mfaEnabled=true, mfaMethod=EMAIL_OTP
    AUTH-->>GW: Success
    GW-->>Client: Email OTP MFA Enabled
    Note over User: Uses verified email on file
```

### Authentication Flow (with MFA)

```mermaid
sequenceDiagram
    participant User
    participant Client
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant DB as Auth DB
    participant NOTIF as Notification Service

    User->>Client: Enter credentials
    Client->>GW: POST /api/v1/auth/login {email, password}
    GW->>AUTH: Forward request
    AUTH->>DB: SELECT * FROM users WHERE email=?
    DB-->>AUTH: User (BCrypt hash)
    AUTH->>AUTH: Verify password (BCrypt)
    alt Valid Credentials
        AUTH->>DB: Check MFA enabled?
        alt MFA Enabled (EMAIL_OTP)
            AUTH->>DB: INSERT LoginChallenge (otp_hash, expires_at, max_attempts=3)
            AUTH->>NOTIF: Publish OTP event (Kafka)
            NOTIF->>User: Send 6-digit OTP via email
            AUTH-->>GW: 200 OK {mfaRequired: true, challengeId, mfaMethods: ["EMAIL_OTP"]}
            GW-->>Client: 200 OK {mfaRequired: true, challengeId}
            Client->>User: Prompt for 6-digit OTP
            User->>Client: Enter OTP
            Client->>GW: POST /api/v1/auth/mfa/verify-challenge {challengeId, otpCode}
            GW->>AUTH: Forward MFA verification
            AUTH->>DB: Verify OTP hash, check expiry & attempts
            alt OTP Valid
                AUTH->>DB: Mark LoginChallenge consumed
                AUTH->>DB: INSERT RefreshToken (new, revoke old)
                AUTH-->>GW: 200 OK {accessToken, refreshToken}
                GW-->>Client: 200 OK {accessToken, refreshToken}
            else OTP Invalid/Expired/Exhausted
                AUTH->>DB: Increment attempts / Delete challenge
                AUTH-->>GW: 401/400 Unauthorized
                GW-->>Client: Error response
            end
        else MFA Not Enabled
            AUTH->>DB: INSERT RefreshToken (new, revoke old)
            AUTH-->>GW: 200 OK {accessToken, refreshToken}
            GW-->>Client: 200 OK {accessToken, refreshToken}
        end
        Client->>Client: Store tokens securely
    else Invalid Credentials
        AUTH->>DB: INSERT LoginAttempt (failed)
        AUTH->>AUTH: Check lockout threshold (5/15min)
        AUTH-->>GW: 401 Unauthorized
        GW-->>Client: 401 Unauthorized
    end
```

### MFA Disable Flow

```mermaid
sequenceDiagram
    participant User
    participant Client
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant DB as Auth DB
    participant NOTIF as Notification Service

    User->>Client: Request MFA disable
    Client->>GW: POST /api/v1/auth/mfa/disable/initiate\nAuthorization: Bearer <token>
    GW->>AUTH: Forward (JWT validated)
    AUTH->>DB: Check MFA enabled, email verified
    AUTH->>DB: INSERT LoginChallenge (purpose=DISABLE_MFA)
    AUTH->>NOTIF: Publish OTP event (Kafka)
    NOTIF->>User: Send 6-digit OTP via email
    AUTH-->>GW: 200 OK {challengeId}
    GW-->>Client: 200 OK {challengeId}
    Client->>User: Prompt for 6-digit OTP
    User->>Client: Enter OTP
    Client->>GW: POST /api/v1/auth/mfa/disable\nAuthorization: Bearer <token>\n{challengeId, otpCode}
    GW->>AUTH: Forward disable verification
    AUTH->>DB: Verify challenge: userId matches, purpose=DISABLE_MFA, OTP valid, not consumed/expired
    alt OTP Valid
        AUTH->>DB: Mark challenge consumed
        AUTH->>DB: Update user: mfaEnabled=false, mfaMethod=null
        AUTH->>DB: Delete unconsumed LOGIN_MFA and DISABLE_MFA challenges
        AUTH-->>GW: 200 OK {message: "MFA disabled successfully"}
        GW-->>Client: 200 OK
    else OTP Invalid/Expired/Exhausted
        AUTH->>DB: Increment attempts / Delete challenge
        AUTH-->>GW: 401/400 Unauthorized
        GW-->>Client: Error response
    end
    Note over Client: No tokens issued — disable is a standalone security operation
```

### Email OTP Implementation

```java
@Service
public class EmailOtpService {
    
    private static final String OTP_CHARS = "0123456789";
    
    @Value("${citicore.auth.mfa.email-otp.length:6}")
    private int otpLength;
    
    @Value("${citicore.auth.mfa.email-otp.expiry-minutes:5}")
    private int expiryMinutes;
    
    @Value("${citicore.auth.mfa.email-otp.max-attempts:3}")
    private int maxAttempts;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    public String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(OTP_CHARS.charAt(secureRandom.nextInt(OTP_CHARS.length())));
        }
        return sb.toString();
    }
    
    public String hashOtp(String otp) {
        // Use BCrypt to hash the OTP for storage
        return BCrypt.hashpw(otp, BCrypt.gensalt());
    }
    
    public boolean verifyOtp(String rawOtp, String hashedOtp) {
        return BCrypt.checkpw(rawOtp, hashedOtp);
    }
    
    public LocalDateTime calculateExpiry() {
        return LocalDateTime.now().plusMinutes(expiryMinutes);
    }
}
```

### Login Challenge Entity

```java
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {
    
    @Id @GeneratedValue
    private Long id;
    
    private Long userId;
    
    @Column(unique = true)
    private String challengeId; // UUID
    
    private String otpHash; // BCrypt hashed OTP
    
    private LocalDateTime expiresAt;
    
    private int attempts = 0;
    private int maxAttempts = 3;
    
    private boolean consumed = false;
    private LocalDateTime consumedAt;
    
    private String purpose = "LOGIN_MFA";
    
    // Business logic methods
    public boolean isExpired() { return expiresAt.isBefore(LocalDateTime.now()); }
    public boolean isExhausted() { return attempts >= maxAttempts; }
    public void incrementAttempts() { this.attempts++; }
    public void markConsumed() { this.consumed = true; this.consumedAt = LocalDateTime.now(); }
}
```

### Enrollment & Recovery

| Scenario | Flow |
|----------|------|
| **First Enrollment** | User enables MFA → Email OTP MFA activated immediately (uses verified email) |
| **Disable MFA** | Two-step: (1) Initiate disable → challenge created, OTP sent; (2) Verify challenge ID + OTP → MFA disabled |
| **Forgot Password + MFA** | Password reset → MFA remains enabled (email unchanged) |
| **Admin Reset** | Admin triggers MFA reset → User re-enables MFA |

### Configuration

```yaml
# application.yml
citicore:
  auth:
    mfa:
      email-otp:
        enabled: true
        length: 6
        expiry-minutes: 5
        max-attempts: 3
        resend-cooldown-minutes: 1
        rate-limit-per-hour: 5
    lockout:
      max-attempts: 5
      duration-minutes: 15
```

### JWT Claims for MFA

```json
{
  "sub": "67890",
  "email": "kyc.officer@citicore.com",
  "roles": ["ROLE_KYC_OFFICER"],
  "permissions": [...],
  "mfaEnabled": true,
  "mfaMethod": "EMAIL_OTP",
  "iat": 1731923400,
  "exp": 1731924300
}
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/auth/mfa/enable` | POST | Enable Email OTP MFA (requires authenticated user, verified email) |
| `/api/v1/auth/mfa/verify-challenge` | POST | Verify OTP during login (challengeId + otpCode) |
| `/api/v1/auth/mfa/resend-otp` | POST | Resend OTP for existing challenge (challengeId) |
| `/api/v1/auth/mfa/disable/initiate` | POST | Initiate MFA disable — creates `DISABLE_MFA` challenge, sends OTP, returns challengeId |
| `/api/v1/auth/mfa/disable` | POST | Verify `DISABLE_MFA` challenge (challengeId + otpCode), disables MFA, no tokens issued |

### Security Considerations

1. **OTP Storage**: Only BCrypt hash stored in database, never raw OTP
2. **Challenge Binding**: Each challenge bound to user, purpose (LOGIN_MFA or DISABLE_MFA), and opaque challengeId
3. **Short Expiry**: 5-minute expiry limits exposure window
4. **Attempt Limits**: Maximum 3 verification attempts per challenge
5. **Resend Limits**: Maximum 3 resends per challenge, 1-minute cooldown between resends
6. **Rate Limiting**: 5 challenge creations per hour per user
7. **Single-Use**: Challenges consumed on successful verification, safe against concurrent requests (pessimistic locking)
8. **Account Enumeration**: Consistent responses regardless of account existence
9. **No Logging**: OTP values, challenge secrets never logged
10. **Email Compromise Risk**: Acknowledged — email OTP less phishing-resistant than TOTP/FIDO2; mitigated by short expiry, attempt limits, and rate limiting
11. **Disable MFA Isolation**: `DISABLE_MFA` challenges are separate from `LOGIN_MFA` challenges; they never issue login tokens. All unconsumed challenges for the user are cleaned up when MFA is disabled.

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 2.0 | 2026-11-23 | Architecture Team | Migrated from TOTP to Email OTP primary |
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR (TOTP primary) |

---

**END OF ADR**
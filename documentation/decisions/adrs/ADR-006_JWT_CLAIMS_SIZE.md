# ADR-006: JWT Claims Size — Permissions in Token

## Status
✅ **APPROVED** (2026-11-18)

## Context

The CitiCore platform uses JWT for stateless authentication. The JWT access token (15min expiry) carries user identity, roles, and authorization information. The platform uses comprehensive RBAC with 9 roles and 28 granular permissions.

**Requirements:**
- Stateless authorization (no central permission service calls on every request)
- Fine-grained permissions in token for service-level authorization
- Near-real-time revocation (≤15 minutes)
- Token size within reasonable limits for HTTP headers

## Decision

**Include roles, permissions, and case assignments directly in the JWT access token.**

### JWT Claims Structure

```json
{
  "iss": "citicore-auth",
  "sub": "12345",
  "email": "customer@example.com",
  "roles": ["ROLE_CUSTOMER"],
  "permissions": [
    "CUSTOMER_PROFILE_READ",
    "CUSTOMER_PROFILE_WRITE",
    "KYC_DOCUMENT_UPLOAD",
    "KYC_DOCUMENT_READ",
    "ACCOUNT_CREATE",
    "ACCOUNT_READ",
    "ACCOUNT_DEPOSIT",
    "ACCOUNT_WITHDRAW",
    "ACCOUNT_TRANSFER",
    "ACCOUNT_STATEMENT",
    "ACCOUNT_CLOSE",
    "TRANSFER_INITIATE",
    "TRANSFER_HISTORY"
  ],
  "kycStatus": "APPROVED",
  "riskScore": 15,
  "assignedCases": [],
  "authorizedCases": [],
  "iat": 1731923400,
  "exp": 1731924300,
  "jti": "uuid-token-id"
}
```

### KYC Officer Token Claims

```json
{
  "sub": "67890",
  "email": "kyc.officer@citicore.com",
  "roles": ["ROLE_KYC_OFFICER"],
  "permissions": [
    "CUSTOMER_PROFILE_READ_ALL",
    "KYC_DOCUMENT_READ",
    "KYC_CASE_REVIEW",
    "KYC_CASE_RECOMMEND",
    "KYC_CASE_REQUEST_INFO",
    "ACCOUNT_READ",
    "ACCOUNT_STATEMENT",
    "TRANSFER_HISTORY"
  ],
  "kycStatus": "N/A",
  "riskScore": 0,
  "assignedCases": ["KYC-20261118-001", "KYC-20261118-002"],
  "authorizedCases": [],
  "iat": 1731923400,
  "exp": 1731924300,
  "jti": "uuid-token-id"
}
```

### KYC Manager Token Claims

```json
{
  "sub": "54321",
  "email": "kyc.manager@citicore.com",
  "roles": ["ROLE_KYC_MANAGER"],
  "permissions": [
    "CUSTOMER_PROFILE_READ_ALL",
    "KYC_DOCUMENT_READ",
    "KYC_CASE_ASSIGN",
    "KYC_CASE_APPROVE",
    "KYC_CASE_REJECT",
    "KYC_CASE_RETURN",
    "KYC_CASE_ESCALATE",
    "KYC_AUDIT_READ",
    "ACCOUNT_READ",
    "ACCOUNT_STATEMENT",
    "TRANSFER_HISTORY"
  ],
  "kycStatus": "N/A",
  "riskScore": 0,
  "assignedCases": [],
  "authorizedCases": ["KYC-20261118-001", "KYC-20261118-003"],
  "iat": 1731923400,
  "exp": 1731924300,
  "jti": "uuid-token-id"
}
```

## Token Generation Rules

| Claim | Source | Refresh Frequency |
|-------|--------|-------------------|
| `sub`, `email` | User entity | On login |
| `roles` | UserRole table | On login + role change (cache eviction) |
| `permissions` | Role-Permission matrix | Computed at token generation |
| `kycStatus` | KycCase latest status | On login + KYC status change |
| `riskScore` | Customer risk profile | On login + daily recalculation |
| `assignedCases` | KycCaseAssignment (officer) | On login + assignment change |
| `authorizedCases` | KycCaseAuthorization (manager) | On login + authorization change |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) Permissions in JWT (Selected)** | All permissions in token | Stateless; no external calls; fast authz | Token size ~2-3KB; revocation ≤15min | ✅ **Selected** |
| **B) Reference to Permission Service** | Token has userId only; services call Permission Service | Small token; real-time revocation | Extra network hop; SPOF; latency | ❌ Rejected |
| **C) Hybrid** | Roles in JWT; permissions from service | Balance | Still needs service call | ❌ Rejected |

## Consequences & Trade-offs

### Positive
- **Stateless Authorization**: No dependency on permission service for authz decisions
- **Performance**: No network round-trip for permission checks
- **Availability**: Authorization works even if permission service is down
- **Scalability**: No centralized permission service bottleneck

### Negative
- **Token Size**: ~2-3KB per token (within HTTP header limits)
- **Revocation Latency**: Up to 15 minutes (JWT expiry) for permission changes
- **Token Payload Size**: Must stay under 8KB (typical header limit)

## Revocation Strategy

| Mechanism | Scope | Latency | Use Case |
|-----------|-------|---------|----------|
| **JWT Short Expiry** | All permissions | ≤15 minutes | Baseline revocation |
| **Refresh Token Revocation** | All permissions | Immediate | Logout, password change, suspension |
| **Redis Permission Cache Eviction** | Cached permissions | Immediate (pub/sub) | Role change, case reassignment |
| **User Status Change** | All access | Immediate | Suspension, termination |
| **Emergency Blocklist** | All access | Immediate | Security incident |

## Token Size Analysis

| Component | Estimated Size |
|-----------|----------------|
| Header | ~100 bytes |
| Standard Claims (sub, iat, exp, etc.) | ~200 bytes |
| Roles (9 max) | ~200 bytes |
| Permissions (28 max) | ~800 bytes |
| Case Assignments (20 max) | ~500 bytes |
| Case Authorizations (20 max) | ~500 bytes |
| KYC Status, Risk Score | ~100 bytes |
| **Total** | **~2.4 KB** |

Well within typical 8KB HTTP header limit.

## Revocation Implementation

```java
@Service
public class PermissionRevocationService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void revokeUserPermissions(Long userId) {
        // 1. Revoke all refresh tokens
        refreshTokenRepository.revokeAllByUser(userId);
        
        // 2. Evict permission cache
        redisTemplate.delete("perm:user:" + userId);
        
        // 3. Publish revocation event
        eventPublisher.publishPermissionRevoked(userId);
    }
    
    public void revokeCaseAssignment(Long caseId, Long officerId) {
        // Invalidate assignment cache
        redisTemplate.delete("kyc:assign:" + caseId);
        
        // Evict officer's permission cache
        redisTemplate.delete("perm:user:" + officerId);
        
        // Publish reassignment event
        eventPublisher.publishCaseReassigned(caseId, officerId);
    }
}
```

## Cache Invalidation Strategy

| Cache | Key Pattern | Invalidation Trigger | TTL |
|-------|-------------|---------------------|-----|
| User Permissions | `perm:user:{userId}` | Role change, status change | 15 min |
| Case Assignments | `kyc:assign:{caseId}` | Assignment change | 5 min |
| Manager Authorizations | `kyc:auth:{managerId}` | Authorization change | 5 min |
| KYC Case Status | `kyc:status:{caseId}` | Status transition | 1 min |
| Role-Permission Matrix | `rbac:matrix` | Admin config change | 1 hour |

## Dependencies

- **DEC-003**: KYC Service Ownership (case assignments in JWT)
- **DEC-005**: SoD Enforcement (case assignments in JWT for SoD check)
- **DEC-009**: MFA Methods (MFA status in token)
- **DEC-012**: Secrets Management (JWT secret in Secrets Manager)

## Future Considerations

| Scenario | Action |
|----------|--------|
| **Token Size Growth** | If permissions exceed 50, consider hybrid approach |
| **Real-time Revocation** | Implement WebSocket push for instant cache invalidation |
| **Cross-Region** | Replicate Redis cache for multi-region deployment |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**
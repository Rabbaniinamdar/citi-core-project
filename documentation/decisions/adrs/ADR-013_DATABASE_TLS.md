# ADR-013: Database TLS — Mandatory TLS for All Production RDS MySQL Connections

## Status
✅ **APPROVED** (2026-11-18)

## Context

CitiCore services connect to AWS RDS for MySQL. Currently, development environments use unencrypted connections. Production requires mandatory TLS 1.2+ for all database connections to protect credentials and data in transit.

**Requirements:**
- All production RDS MySQL connections must use TLS
- RDS configured with `require_secure_transport=ON`
- Spring Boot connections must validate RDS certificate and hostname
- CA trust configuration maintained
- Unencrypted connections rejected
- Local development exceptions documented separately

## Decision

**Mandatory TLS 1.2+ for all production RDS MySQL connections with full certificate validation.**

### RDS Configuration

```yaml
# RDS Parameter Group
ParameterGroup:
  Family: mysql8.0
  Parameters:
    - Name: require_secure_transport
      Value: "ON"
      ApplyMethod: immediate
    - Name: tls_version
      Value: "TLSv1.2,TLSv1.3"
      ApplyMethod: immediate
```

### Spring Boot Datasource Configuration

```yaml
# Production application.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?useSSL=true&requireSSL=true&verifyServerCertificate=true&enabledTLSProtocols=TLSv1.2,TLSv1.3
    hikari:
      connectionInitSql: "SET SESSION ssl_mode=REQUIRED"
    # Credentials from AWS Secrets Manager (DEC-012)

# SSL Properties (if needed for custom truststore)
javax.net.ssl.trustStore: /opt/certs/rds-ca-truststore.jks
javax.net.ssl.trustStorePassword: ${TRUSTSTORE_PASSWORD}
```

### RDS Certificate Validation

| Aspect | Configuration |
|--------|---------------|
| **CA Certificate** | AWS RDS CA 2019 (`rds-ca-2019-root.pem`) |
| **Validation** | `verifyServerCertificate=true` (validates hostname + CA chain) |
| **Trust Store** | Bundle AWS RDS CA certs into JKS truststore |
| **Rotation** | AWS rotates CA every 5 years; monitor AWS announcements; update truststore before expiry |

### Certificate Trust Store Setup

```bash
# 1. Download AWS RDS CA bundle
curl -o rds-combined-ca-bundle.pem https://truststore.pki.rds.amazonaws.com/global/rds-combined-ca-bundle.pem

# 2. Create JKS truststore
keytool -importcert -alias rds-ca-2019 -file rds-combined-ca-bundle.pem \
  -keystore rds-ca-truststore.jks -storepass ${TRUSTSTORE_PASSWORD} -noprompt

# 3. Verify
keytool -list -keystore rds-ca-truststore.jks -storepass ${TRUSTSTORE_PASSWORD}
```

```yaml
# Kubernetes Deployment - Mount truststore
spec:
  template:
    spec:
      volumes:
        - name: rds-truststore
          secret:
            secretName: rds-ca-truststore
      containers:
        - name: auth-service
          volumeMounts:
            - name: rds-truststore
              mountPath: /opt/certs
              readOnly: true
          env:
            - name: TRUSTSTORE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: rds-ca-truststore
                  key: password
```

### Local Development Exception

| Environment | TLS Requirement | Configuration |
|-------------|-----------------|---------------|
| **Production (EKS)** | **Mandatory** | `requireSSL=true&requireSSL=true&verifyServerCertificate=true` |
| **Staging (EKS)** | **Mandatory** | Same as production |
| **Local (Docker Compose)** | **Optional** | `useSSL=false` (MySQL container without TLS) |
| **Integration Tests** | **Mandatory** | Testcontainers with TLS enabled |

**Never weaken production certificate validation to resolve connection problems.** Instead:
1. Verify RDS endpoint matches certificate SAN
2. Update truststore with current AWS CA
3. Check security group / NACL allows TLS port 3306
4. Verify `require_secure_transport=ON` on RDS

### Connection Security Matrix

| Connection | TLS Required | Certificate Validation | Auth Method |
|------------|--------------|------------------------|-------------|
| **Service → RDS (Prod)** | ✅ Yes | ✅ Full (CA + Hostname) | Username/Password (Secrets Manager) |
| **Service → RDS (Staging)** | ✅ Yes | ✅ Full | Username/Password |
| **Service → RDS (Local)** | ❌ No (MySQL container) | N/A | Username/Password |
| **Read Replica** | ✅ Yes | ✅ Full | Same credentials |
| **Cross-Region Replica** | ✅ Yes | ✅ Full | Same credentials |

### Monitoring & Alerting

| Metric | Alert Threshold | Action |
|--------|-----------------|--------|
| `DatabaseConnectionsUnencrypted` | > 0 | Critical: Unencrypted connection detected |
| `TLSCertificateExpiryDays` | < 30 | Warning: Certificate expiring soon |
| `TLSSessionErrors` | > 5/min | Warning: TLS handshake failures |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) Mandatory TLS + Full Validation (Selected)** | `require_secure_transport=ON`; full cert validation | Maximum security; compliance | Cert rotation management | ✅ **Selected** |
| **B) TLS Optional** | Allow unencrypted fallback | Easier debugging | Credentials in cleartext; non-compliant | ❌ Rejected |
| **C) TLS Without Hostname Verification** | `verifyServerCertificate=false` | Avoids cert rotation issues | MITM vulnerable; non-compliant | ❌ Rejected |
| **D) mTLS (Client Certs)** | Mutual TLS with client certificates | Strongest auth | Complex cert lifecycle; overkill | Future consideration |

## Consequences & Trade-offs

### Positive
- **Data in Transit Protection**: Credentials and PII encrypted on wire
- **Regulatory Compliance**: Meets RBI, PCI-DSS, GDPR transit encryption requirements
- **Defense in Depth**: Even if VPC compromised, DB traffic unreadable
- **Audit Trail**: TLS handshake logs available for forensic analysis

### Negative
- **Certificate Management**: AWS CA rotation every 5 years requires truststore update
- **Connection Overhead**: TLS handshake adds ~1-2ms per new connection (mitigated by HikariCP pooling)
- **Debugging Complexity**: Cannot inspect raw SQL on wire (use DB audit logs instead)

## Security Implications

- **Credential Protection**: Database passwords never transmitted in cleartext
- **PII Protection**: Query results containing PAN, Aadhaar encrypted in transit
- **MITM Prevention**: Certificate validation prevents man-in-the-middle
- **Compliance**: Satisfies "encryption in transit" requirements

## Implementation Considerations

1. **Deploy Order**: RDS parameter group → truststore secret → service deployments
2. **Rollout Strategy**: Blue/green deploy to verify TLS before switching traffic
3. **Testing**: Verify connection works with `useSSL=true&verifyServerCertificate=true`
4. **Rollback Plan**: Keep previous parameter group; revert if connections fail
5. **Documentation**: Record CA expiry dates; set calendar reminders

## Dependencies

- **DEC-001**: Database Topology (RDS instance)
- **DEC-011**: MySQL Encryption (KMS for storage; TLS for transit)
- **DEC-012**: Secrets Management (DB credentials in Secrets Manager)
- **AWS**: RDS, Certificate Manager (for CA bundle), Secrets Manager, EKS

## Future Considerations

| Scenario | Action |
|----------|--------|
| **AWS CA Rotation (2024/2029)** | Pre-deploy new truststore; test; switch at rotation date |
| **mTLS Adoption** | Issue client certs via ACM Private CA; configure RDS for `require_secure_transport=ON` + client cert verification |
| **TLS 1.3 Only** | Deprecate TLS 1.2 when MySQL 8.0+ widely supports 1.3 |
| **Service Mesh** | Offload TLS to Istio/Linkerd; use SPIFFE/SPIRE for identity |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**
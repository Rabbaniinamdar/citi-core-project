# ADR-011: MySQL Encryption — AWS RDS KMS Storage Encryption + Selective Application-Level Envelope Encryption

## Status
✅ **APPROVED** (2026-11-18)

## Context

CitiCore stores sensitive customer data (PII, KYC documents, financial records) in AWS RDS for MySQL. Encryption at rest and in transit is required for regulatory compliance (RBI, PCI-DSS, GDPR).

**Key Technical Constraint:** AWS RDS for MySQL does **not** support native MySQL TDE (Transparent Data Encryption). RDS TDE is only available for Oracle and SQL Server engines.

**Requirements:**
- Encryption at rest for all database data
- Encryption must cover backups and snapshots
- Selective field-level encryption for high-sensitivity PII (PAN, Aadhaar, etc.)
- Keys managed through AWS KMS
- Key rotation and access policies defined
- Minimize sensitive data retention

## Decision

**Use AWS RDS storage encryption with customer-managed KMS key for transparent encryption at rest, plus selective application-level envelope encryption for specific sensitive fields.**

### Encryption Layers

| Layer | Scope | Implementation | Key Management |
|-------|-------|----------------|----------------|
| **Layer 1: Storage Encryption** | All data in RDS instance (tables, indexes, logs, backups, snapshots, read replicas) | AWS RDS encryption (AES-256) | Customer-managed KMS key (`citicore-rds-encryption-key`) |
| **Layer 2: Application-Level Envelope Encryption** | Selected sensitive fields only (PAN, Aadhaar, passport number, etc.) | AES-256-GCM envelope encryption in application code | Data keys wrapped by KMS (`citicore-field-encryption-key`) |

### Layer 1: RDS Storage Encryption (Transparent)

```yaml
# RDS Instance Configuration
Engine: mysql
EngineVersion: 8.0.35
StorageEncrypted: true
KmsKeyId: arn:aws:kms:region:account:key/citicore-rds-encryption-key
# Covers:
# - Database storage
# - Automated backups
# - Manual snapshots
# - Read replicas
# - Redo logs, undo logs, temp tables
```

**Key Policy:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowRDSService",
      "Effect": "Allow",
      "Principal": { "Service": "rds.amazonaws.com" },
      "Action": ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*"],
      "Resource": "*"
    },
    {
      "Sid": "AllowAdminRotation",
      "Effect": "Allow",
      "Principal": { "AWS": "arn:aws:iam::account:role/citicore-db-admin" },
      "Action": ["kms:DescribeKey", "kms:EnableKeyRotation", "kms:DisableKeyRotation"],
      "Resource": "*"
    }
  ]
}
```

### Layer 2: Application-Level Envelope Encryption (Selective)

**Fields Requiring Additional Encryption:**

| Field | Table | Schema | Reason |
|-------|-------|--------|--------|
| `pan_number` | `kyc_documents` / `kyc_document_refs` | `citicore_userdb` / `citicore_kycdb` | PAN = financial identifier |
| `aadhaar_number` (masked) | `kyc_documents` / `kyc_document_refs` | `citicore_userdb` / `citicore_kycdb` | Aadhaar = national ID |
| `passport_number` | `kyc_documents` / `kyc_document_refs` | `citicore_userdb` / `citicore_kycdb` | Passport = travel document |
| `account_number` (if exposed) | `accounts` | `citicore_account` | Financial account identifier |
| `nominee_name` | `accounts` | `citicore_account` | PII |

**Envelope Encryption Implementation:**

```java
@Component
public class FieldEncryptionService {
    
    private final KmsClient kmsClient;
    private final String fieldEncryptionKeyId; // citicore-field-encryption-key
    private final AesGcmEncryptor encryptor; // AES-256-GCM
    
    public String encryptField(String plaintext) {
        // 1. Generate data key
        GenerateDataKeyResponse keyResp = kmsClient.generateDataKey(
            GenerateDataKeyRequest.builder()
                .keyId(fieldEncryptionKeyId)
                .keySpec(DataKeySpec.AES_256)
                .build());
        
        // 2. Encrypt plaintext with plaintext data key
        byte[] ciphertext = encryptor.encrypt(
            keyResp.plaintext().asByteArray(),
            plaintext.getBytes(StandardCharsets.UTF_8)
        );
        
        // 3. Return encrypted data key + ciphertext (Base64 encoded)
        return Base64.getEncoder().encodeToString(
            ByteBuffer.allocate(4 + keyResp.ciphertextBlob().asByteArray().length + ciphertext.length)
                .putInt(keyResp.ciphertextBlob().asByteArray().length)
                .put(keyResp.ciphertextBlob().asByteArray())
                .put(ciphertext)
                .array()
        );
    }
    
    public String decryptField(String encryptedPackage) {
        // 1. Parse package
        ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedPackage));
        int keyLen = buf.getInt();
        byte[] encryptedKey = new byte[keyLen];
        buf.get(encryptedKey);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);
        
        // 2. Decrypt data key
        DecryptResponse keyResp = kmsClient.decrypt(DecryptRequest.builder()
            .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
            .build());
        
        // 3. Decrypt ciphertext with plaintext data key
        return new String(encryptor.decrypt(keyResp.plaintext().asByteArray(), ciphertext), StandardCharsets.UTF_8);
    }
}
```

**Database Storage Format:**
```sql
-- Encrypted fields stored as TEXT (Base64 encoded package)
ALTER TABLE kyc_documents 
  MODIFY COLUMN pan_number TEXT,
  MODIFY COLUMN aadhaar_number TEXT,
  MODIFY COLUMN passport_number TEXT;
```

### Key Rotation & Access Policies

| Key | Rotation | Access | Audit |
|-----|----------|--------|-------|
| `citicore-rds-encryption-key` | Automatic annual (KMS managed) | RDS service only | CloudTrail |
| `citicore-field-encryption-key` | Manual annual (triggered by security team) | `FieldEncryptionService` IAM role | CloudTrail + application audit log |

**Key Rotation Process (Field Key):**
1. Create new KMS key version (or new key with alias update)
2. Re-encrypt all affected fields using new key (batch job)
3. Update alias to new key
4. Verify decryption works
5. Schedule old key deletion (30-day waiting period)

### Backup & Snapshot Coverage

| Artifact | Encrypted By | Mechanism |
|----------|--------------|-----------|
| **Automated Backups** | RDS Storage Encryption | Transparent; same KMS key |
| **Manual Snapshots** | RDS Storage Encryption | Transparent; same KMS key |
| **Cross-Region Snapshots** | RDS Storage Encryption | Re-encrypted with destination region KMS key |
| **Point-in-Time Recovery** | RDS Storage Encryption | Transparent |

### What NOT to Do

| Anti-Pattern | Reason |
|--------------|--------|
| ❌ Direct KMS encrypt/decrypt per field operation | Latency; cost; throttling risk |
| ❌ Store plaintext keys in application config | Defeats encryption purpose |
| ❌ Encrypt entire rows/records | Overhead; breaks indexing; not needed |
| ❌ Use RDS MySQL native TDE | Not supported on RDS MySQL |
| ❌ Double-encrypt everything | Waste; complexity; no added security |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) RDS KMS + Selective App-Level (Selected)** | Transparent storage + field-level for PII | Covers all data; minimal overhead; regulatory compliance | App complexity for field encryption | ✅ **Selected** |
| **B) RDS KMS Only** | Storage encryption only | Simple; zero app changes | No field-level control; all data same protection level | Insufficient for PAN/Aadhaar |
| **C) App-Level Only (No RDS KMS)** | Field encryption for all sensitive data | Full control | No backup/snapshot encryption; ops burden | ❌ Rejected |
| **C) MySQL TDE (Native)** | MySQL built-in TDE | Transparent | **Not supported on RDS MySQL** | ❌ Not available |

## Consequences & Trade-offs

### Positive
- **Complete Coverage**: All data at rest encrypted (storage + critical fields)
- **Regulatory Compliance**: Meets RBI, PCI-DSS, GDPR encryption requirements
- **Operational Simplicity**: RDS handles storage encryption transparently
- **Selective Protection**: High-value PII gets additional envelope encryption
- **Key Hierarchy**: KMS manages root keys; app manages data keys per envelope

### Negative
- **Application Complexity**: Field encryption/decryption logic in service layer
- **Indexing Limitations**: Encrypted fields cannot be indexed for search (use hashed lookup columns)
- **Migration Effort**: Existing plaintext fields must be encrypted on deployment
- **Key Rotation**: Field key rotation requires batch re-encryption job

## Security Implications

- **Defense in Depth**: Storage encryption + field encryption = two layers
- **Blast Radius**: Compromised app credentials ≠ decrypted PII (needs KMS access)
- **Audit Trail**: All KMS operations logged to CloudTrail
- **Least Privilege**: App IAM role only has `kms:GenerateDataKey`, `kms:Decrypt` on field key

## Implementation Considerations

1. **Deployment Order**: RDS KMS key → RDS instance → Field encryption key → App deployment
2. **Existing Data Migration**: One-time job to encrypt existing PAN/Aadhaar fields
3. **Search Support**: Add `pan_hash` (SHA-256) column for exact-match lookups
4. **Testing**: Verify backup/restore decrypts correctly; verify cross-region snapshot
5. **Monitoring**: Alert on KMS throttling; field encryption latency > 50ms

## Dependencies

- **DEC-001**: Database Topology (RDS instance with schemas)
- **DEC-012**: Secrets Management (DB credentials in Secrets Manager)
- **DEC-013**: Database TLS (transit encryption)
- **AWS**: KMS, RDS, IAM, CloudTrail

## Future Considerations

| Scenario | Action |
|----------|--------|
| **Key Compromise** | Immediate key rotation; re-encrypt affected fields |
| **Regulatory Change** | Add fields to envelope encryption list |
| **Performance** | Cache decrypted values in Redis (TTL: 5 min) with encryption context |
| **Multi-Region** | Replicate KMS keys; use multi-region keys for field encryption |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**
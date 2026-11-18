# ADR-012: Secrets Management — AWS Secrets Manager

## Status
✅ **APPROVED** (2026-11-18)

## Context

CitiCore currently stores database credentials, JWT secrets, and API keys in environment variables within Docker Compose files. This approach is not suitable for production deployment on AWS EKS.

**Requirements:**
- Store all sensitive credentials securely
- No hardcoded secrets in source code, Docker images, Git, or Kubernetes manifests
- Least-privilege access for workloads
- Automated secret rotation
- Audit trail for all secret access
- Integration with EKS workload identity

## Decision

**Use AWS Secrets Manager for all secret storage, with IAM Roles for Service Accounts (IRSA) for EKS workload access.**

### Secret Inventory

| Secret Name | Description | Rotation | Consumers |
|-------------|-------------|----------|-----------|
| `citicore/auth/db-credentials` | Auth Service DB user/password | 90 days | Auth Service |
| `citicore/kyc/db-credentials` | KYC Workflow DB user/password | 90 days | KYC Workflow Service |
| `citicore/user/db-credentials` | User Service DB user/password | 90 days | User Service |
| `citicore/account/db-credentials` | Account Service DB user/password | 90 days | Account Service |
| `citicore/transaction/db-credentials` | Transaction Service DB user/password | 90 days | Transaction Service |
| `citicore/ledger/db-credentials` | Ledger Service DB user/password | 90 days | Ledger Service |
| `citicore/compliance/db-credentials` | Compliance Service DB user/password | 90 days | Compliance Service |
| `citicore/auth/jwt-secret` | JWT signing key (HS256, 256-bit) | 180 days | All Services (via Auth) |
| `citicore/s3/kms-key-id` | KMS key ARN for S3 SSE | N/A | User Service, KYC Service |
| `citicore/redis/credentials` | Redis/ElastiCache auth token | 90 days | All Services |
| `citicore/kafka/sasl-credentials` | MSK SASL/SCRAM credentials | 90 days | All Services |
| `citicore/sendgrid/api-key` | SendGrid email API key | 180 days | Notification Service |
| `citicore/google/vision-credentials` | Google Cloud Vision service account | 365 days | User Service (OCR) |

### IRSA Configuration

```yaml
# Kubernetes ServiceAccount with IRSA
apiVersion: v1
kind: ServiceAccount
metadata:
  name: auth-service
  namespace: citicore-prod
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::account:role/citicore-auth-service-secrets
---
# IAM Role for Auth Service
apiVersion: iam.amazonaws.com/v1alpha1
kind: IAMRole
metadata:
  name: citicore-auth-service-secrets
spec:
  trustPolicy:
    Version: "2012-10-17"
    Statement:
      - Effect: Allow
        Principal:
          Federated: "arn:aws:iam::account:oidc-provider/oidc.eks.region.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE"
        Action: "sts:AssumeRoleWithWebIdentity"
        Condition:
          StringEquals:
            "oidc.eks.region.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE:sub": "system:serviceaccount:citicore-prod:auth-service"
  policies:
    - PolicyName: SecretsAccess
      PolicyDocument:
        Version: "2012-10-17"
        Statement:
          - Effect: Allow
            Action:
              - "secretsmanager:GetSecretValue"
              - "secretsmanager:DescribeSecret"
            Resource:
              - "arn:aws:secretsmanager:region:account:secret:citicore/auth/db-credentials*"
              - "arn:aws:secretsmanager:region:account:secret:citicore/auth/jwt-secret*"
              - "arn:aws:secretsmanager:region:account:secret:citicore/redis/credentials*"
              - "arn:aws:secretsmanager:region:account:secret:citicore/kafka/sasl-credentials*"
```

### Spring Boot Integration

```yaml
# application.yml (no secrets in config)
spring:
  config:
    import: "aws-secretsmanager:"
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/citicore_authdb?useSSL=true&requireSSL=true
    # username/password injected from Secrets Manager
  jpa:
    hibernate:
      ddl-auto: validate

# AWS Secrets Manager config
aws:
  secretsmanager:
    region: ${AWS_REGION}
    prefix: /citicore
    profile-separator: _
```

```java
@Configuration
@EnableConfigurationProperties
public class SecretsManagerConfig {
    
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${DB_USERNAME:}") String username,
            @Value("${DB_PASSWORD:}") String password) {
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
}
```

### Secret Rotation

| Secret Type | Rotation Frequency | Mechanism |
|-------------|-------------------|-----------|
| **Database Credentials** | 90 days | Lambda function: generate new password → update RDS → update secret → restart pods (rolling) |
| **JWT Secret** | 180 days | Manual: generate new key → update secret → rolling restart all services |
| **Redis/Kafka Credentials** | 90 days | Lambda: rotate → update secret → rolling restart |
| **API Keys (SendGrid, Google)** | Per vendor policy | Manual update in Secrets Manager |

**Rotation Lambda (Database):**
```python
import boto3
import pymysql
import json

def rotate_db_secret(secret_arn, role_arn):
    client = boto3.client('secretsmanager')
    rds = boto3.client('rds')
    
    # 1. Get current secret
    current = client.get_secret_value(SecretId=secret_arn)
    creds = json.loads(current['SecretString'])
    
    # 2. Generate new password
    new_password = generate_password(32)
    
    # 3. Update RDS user
    conn = pymysql.connect(
        host=creds['host'], user=creds['username'], 
        password=creds['password'], database=creds['dbname']
    )
    with conn.cursor() as cur:
        cur.execute(f"ALTER USER '{creds['username']}'@'%' IDENTIFIED BY '{new_password}'")
    conn.commit()
    
    # 4. Put new secret version
    new_secret = {**creds, 'password': new_password}
    client.put_secret_value(
        SecretId=secret_arn,
        SecretString=json.dumps(new_secret),
        VersionStages=['AWSCURRENT']
    )
```

### Access Patterns

| Pattern | Description | Example |
|---------|-------------|---------|
| **Bootstrap** | Init container fetches secrets → writes to memory/files → main container starts | Init container with AWS CLI |
| **Runtime Injection** | Spring Cloud AWS Secrets Manager reads at startup | `spring.config.import: aws-secretsmanager:` |
| **Sidecar** | Secrets Manager Agent sidecar watches for changes | Not needed for current requirements |

### Audit & Monitoring

| Control | Implementation |
|---------|----------------|
| **Access Logging** | CloudTrail logs all `GetSecretValue` calls |
| **Rotation Logging** | Lambda logs rotation steps; SNS notification on completion |
| **Anomaly Detection** | GuardDuty alerts on unusual secret access patterns |
| **Compliance** | AWS Config rule: `secretsmanager-secret-periodic-rotation` |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) AWS Secrets Manager + IRSA (Selected)** | Native AWS, IRSA, rotation, audit | Fully managed; EKS native; rotation; least privilege | Cost ($0.40/secret/month) | ✅ **Selected** |
| **B) HashiCorp Vault** | Self-hosted Vault on EKS | Full control; multi-cloud; dynamic secrets | Operational overhead; HA setup; upgrade burden | ❌ Rejected |
| **C) Kubernetes Secrets** | Base64 encoded in etcd | Simple; no external dependency | Not encrypted by default; no rotation; RBAC limited | ❌ Rejected |
| **D) Doppler / 1Password / External** | SaaS secrets management | Good UX; multi-env | Vendor lock-in; cost; network dependency | ❌ Rejected |

## Consequences & Trade-offs

### Positive
- **Zero Hardcoded Secrets**: All credentials externalized
- **Least Privilege**: Per-service IAM roles with scoped secret access
- **Automated Rotation**: Lambda-based rotation for DB credentials
- **Audit Trail**: CloudTrail captures all access
- **EKS Native**: IRSA integrates with Kubernetes RBAC

### Negative
- **Cost**: ~$0.40/secret/month + API calls (~$15-20/month total)
- **Network Dependency**: Requires VPC endpoints or NAT for Secrets Manager access
- **Bootstrap Complexity**: Init containers or Spring Cloud AWS needed
- **Rotation Downtime**: Rolling restart required for credential changes

## Security Implications

- **No Secrets in Git/Docker**: Enforced by CI/CD pipeline scans (TruffleHog)
- **Encryption at Rest**: Secrets Manager uses AWS KMS (AES-256)
- **Encryption in Transit**: TLS 1.2+ for all Secrets Manager API calls
- **Least Privilege**: Each service role only accesses its own secrets
- **Key Hierarchy**: Secrets Manager encrypts with AWS KMS; KMS key policy controlled by security team

## Implementation Considerations

1. **VPC Endpoint**: Create `com.amazonaws.region.secretsmanager` interface endpoint for private access
2. **Secret Naming Convention**: `/citicore/{service}/{purpose}` for consistency
3. **Cross-Account**: If multi-account, use resource policies for cross-account access
4. **Testing**: LocalStack for local development; mock in unit tests
5. **Migration**: One-time script to move existing env vars to Secrets Manager

## Dependencies

- **DEC-001**: Database Topology (DB credentials per schema)
- **DEC-011**: MySQL Encryption (KMS key for Secrets Manager encryption)
- **DEC-013**: Database TLS (TLS config in secret)
- **AWS**: Secrets Manager, IAM, KMS, Lambda, EventBridge, SNS, CloudTrail, VPC Endpoints

## Future Considerations

| Scenario | Action |
|----------|--------|
| **Dynamic Secrets** | Use Vault-style dynamic DB credentials (RDS IAM auth) |
| **Multi-Region** | Replicate secrets to DR region; use multi-region KMS |
| **Certificate Management** | Add TLS certs to Secrets Manager (or use ACM) |
| **Service Mesh** | Istio SDS integration for mTLS certificates |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**
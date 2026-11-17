# CitiCore Banking Platform - API Documentation

## Overview

This document describes the REST APIs for the CitiCore Banking Platform. All APIs are versioned under `/api/v1/` and require JWT authentication unless otherwise noted.

**Base URL:** `https://api.citicore.com/api/v1` (via API Gateway port 8080)

**Authentication:** Bearer token (JWT) in `Authorization` header
```
Authorization: Bearer <access_token>
```

**Idempotency:** Mutating endpoints require `Idempotency-Key` header (UUID v4)

---

## Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes* | Bearer JWT token (*except auth endpoints) |
| `Idempotency-Key` | Yes* | UUID for mutating endpoints (*POST/PUT/DELETE) |
| `Content-Type` | Yes | `application/json` |
| `Accept` | No | `application/json` (default) |
| `X-Request-ID` | No | Client-generated correlation ID |

---

## Error Response Format

All errors follow RFC 7807 Problem Details:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/accounts/create",
  "validationErrors": [
    {
      "field": "amount",
      "message": "Amount must be positive"
    }
  ]
}
```

### Common HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request (validation error) |
| 401 | Unauthorized (invalid/expired token) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Not Found |
| 409 | Conflict (duplicate resource) |
| 422 | Unprocessable Entity (business rule violation) |
| 429 | Too Many Requests (rate limited) |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

---

## 1. Authentication Service (auth-service)

### 1.1 Register User
**POST** `/auth/register`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (200):**
```json
{
  "message": "User registered. Verification code sent to email."
}
```

**Errors:**
- 409: Email already registered
- 400: Validation failed (password policy, email format)

---

### 1.2 Login
**POST** `/auth/login`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**Errors:**
- 401: Invalid credentials
- 403: Email not verified / Account not active
- 429: Rate limited (5 req/min)

---

### 1.3 Refresh Access Token
**POST** `/auth/refresh-token`

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl..."
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**Errors:**
- 401: Invalid/expired/revoked refresh token

---

### 1.4 Logout
**POST** `/auth/logout`

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJl..."
}
```

**Response (200):**
```json
{
  "message": "Logged out successfully"
}
```

---

### 1.5 Verify Email
**POST** `/auth/verify`

**Request:**
```json
{
  "email": "user@example.com",
  "verificationCode": "A1B2C3D4"
}
```

**Response (200):**
```json
{
  "message": "Account verified successfully"
}
```

**Errors:**
- 400: Invalid/expired code
- 429: Rate limited

---

### 1.6 Resend Verification Code
**POST** `/auth/resend?email=user@example.com`

**Response (200):**
```json
{
  "message": "Verification code sent"
}
```

**Errors:**
- 400: Already verified / Wait before requesting
- 429: Rate limited

---

## 2. User Service (user-service)

### 2.1 Create Profile
**POST** `/users/profile`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "user@example.com",
  "phoneNumber": "+919876543210",
  "dateOfBirth": "1990-01-15",
  "address": "123 Main St",
  "city": "Mumbai",
  "state": "Maharashtra",
  "pincode": "400001"
}
```

**Response (201):**
```json
{
  "id": 1,
  "authUserId": 123,
  "firstName": "John",
  "lastName": "Doe",
  "email": "user@example.com",
  "phoneNumber": "+919876543210",
  "dateOfBirth": "1990-01-15",
  "address": "123 Main St",
  "city": "Mumbai",
  "state": "Maharashtra",
  "pincode": "400001",
  "kycStatus": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### 2.2 Get My Profile
**GET** `/users/profile`

**Headers:** `Authorization: Bearer <token>`

**Response (200):** Same as create response

---

### 2.3 Update Profile
**PUT** `/users/profile`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:** (All fields optional)
```json
{
  "firstName": "John",
  "lastName": "Doe Updated",
  "phoneNumber": "+919876543211",
  "address": "456 New St",
  "city": "Delhi",
  "state": "Delhi",
  "pincode": "110001"
}
```

**Response (200):** Updated profile

---

### 2.4 Upload KYC Document
**POST** `/users/kyc/documents`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`
**Content-Type:** `multipart/form-data`

**Form Data:**
- `file`: Document file (PDF/JPG/PNG, max 10MB)
- `documentType`: `PAN_CARD` | `AADHAAR_CARD` | `PASSPORT` | `DRIVING_LICENSE` | `VOTER_ID`

**Response (201):**
```json
{
  "id": 1,
  "authUserId": 123,
  "documentType": "PAN_CARD",
  "fileName": "pan_card.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "s3Key": "kyc/123/pan_card_abc123.pdf",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Errors:**
- 400: Invalid file type/size, KYC already approved
- 409: Document type already exists (use PUT to replace)

---

### 2.5 Get My KYC Documents
**GET** `/users/kyc/documents`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
[
  {
    "id": 1,
    "documentType": "PAN_CARD",
    "fileName": "pan_card.pdf",
    "contentType": "application/pdf",
    "fileSize": 102400,
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

---

### 2.6 Get KYC Status
**GET** `/users/kyc/status`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "kycStatus": "UNDER_REVIEW",
  "message": "Documents submitted, under review"
}
```

---

### 2.7 Admin: Review KYC (Admin Only)
**POST** `/users/admin/kyc/review/{userId}`

**Headers:** `Authorization: Bearer <admin_token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "status": "APPROVED"
}
```

**Response (200):**
```json
{
  "id": 1,
  "authUserId": 123,
  "kycStatus": "APPROVED",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

**Errors:**
- 403: Not admin
- 400: Invalid status / not UNDER_REVIEW

---

### 2.8 Admin: List Pending KYC (Admin Only)
**GET** `/users/admin/kyc/pending`

**Headers:** `Authorization: Bearer <admin_token>`

**Response (200):** Array of user profiles with `kycStatus: UNDER_REVIEW`

---

## 3. Account Service (account-service)

### 3.1 Create Account
**POST** `/accounts/create`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "accountType": "SAVINGS",
  "initialDeposit": "5000.00"
}
```

**Response (201):**
```json
{
  "id": 1,
  "accountNumber": "CITI000000000001",
  "authUserId": 123,
  "accountType": "SAVINGS",
  "balance": "5000.00",
  "status": "ACTIVE",
  "isDefault": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Errors:**
- 409: Account type already exists for user
- 403: KYC not approved
- 422: Initial deposit below minimum (SAVINGS: ₹1000, CURRENT: ₹5000)

---

### 3.2 Get My Accounts
**GET** `/accounts/my-accounts`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
[
  {
    "id": 1,
    "accountNumber": "CITI000000000001",
    "accountType": "SAVINGS",
    "balance": "5000.00",
    "status": "ACTIVE",
    "isDefault": true,
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

---

### 3.3 Deposit
**POST** `/accounts/deposit`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "accNo": "CITI000000000001",
  "amount": "10000.00",
  "txnRef": "DEP_abc123"
}
```

**Response (200):**
```json
{
  "message": "Deposit successful",
  "accountNumber": "CITI000000000001"
}
```

**Errors:**
- 404: Account not found
- 403: Unauthorized (not your account)
- 409: Duplicate txnRef
- 422: Amount must be positive

---

### 3.4 Withdraw
**POST** `/accounts/withdraw`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "accNo": "CITI000000000001",
  "amount": "5000.00",
  "txnRef": "WIT_xyz789"
}
```

**Response (200):**
```json
{
  "message": "Withdrawal successful",
  "accountNumber": "CITI000000000001"
}
```

**Errors:**
- 404: Account not found
- 403: Unauthorized
- 409: Duplicate txnRef
- 422: Insufficient balance / below minimum balance

---

### 3.5 Get Balance
**GET** `/accounts/balance/{accNo}`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "message": "Balance fetched",
  "data": "15000.00"
}
```

**Errors:**
- 404: Account not found
- 403: Unauthorized

---

### 3.6 Get Mini Statement (Last 10 Transactions)
**GET** `/accounts/mini-statement/{accNo}`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "message": "Mini statement fetched",
  "data": [
    {
      "id": 1,
      "accountNumber": "CITI000000000001",
      "transactionType": "CREDIT",
      "amount": "10000.00",
      "balanceAfterTxn": "15000.00",
      "txnRef": "DEP_abc123",
      "description": "Deposit",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

---

### 3.7 Get Full Statement (Paginated)
**GET** `/accounts/statement/{accNo}?page=0&size=10`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "message": "Statement fetched",
  "data": {
    "content": [
      {
        "id": 1,
        "accountNumber": "CITI000000000001",
        "transactionType": "CREDIT",
        "amount": "10000.00",
        "balanceAfterTxn": "15000.00",
        "txnRef": "DEP_abc123",
        "description": "Deposit",
        "createdAt": "2024-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

---

### 3.8 Validate Transfer (Internal - Feign)
**GET** `/accounts/validate-transfer?accNo={accNo}&amount={amount}`

**Headers:** `Authorization: Bearer <service_token>` (Service-to-service)

**Response (200):** Empty body - validation passed

**Errors:**
- 404: Account not found
- 422: Insufficient balance / below minimum balance

---

## 4. Transaction Service (transaction-service)

### 4.1 Initiate Transfer
**POST** `/transaction/transfer`

**Headers:** `Authorization: Bearer <token>`, `Idempotency-Key: <uuid>`

**Request:**
```json
{
  "fromAccount": "CITI000000000001",
  "toAccount": "CITI000000000002",
  "amount": "5000.00",
  "type": "TRANSFER"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Transfer initiated successfully",
  "data": "TXN_abc123def456"
}
```

**Errors:**
- 400: Invalid request / missing idempotency key
- 409: Duplicate idempotency key (returns existing txnRef)
- 422: Insufficient balance / daily limit exceeded / KYC not complete
- 404: From account not found

---

### 4.2 Get Transaction Status
**GET** `/transaction/status/{txnRef}`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "success": true,
  "message": "Status fetched",
  "data": "CREDIT_SUCCESS"
}
```

**Status Values:** `INITIATED`, `DEBIT_SUCCESS`, `CREDIT_SUCCESS`, `FAILED`, `REVERSED`

---

### 4.3 Get Transaction Details
**GET** `/transaction/{txnRef}`

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "txnId": "TXN_abc123def456",
  "fromAccount": "CITI000000000001",
  "toAccount": "CITI000000000002",
  "amount": "5000.00",
  "status": "CREDIT_SUCCESS",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

### 4.4 Get Transaction History (Paginated)
**GET** `/transaction/history?status=CREDIT_SUCCESS&page=0&size=10&startDate=2024-01-01&endDate=2024-01-31`

**Headers:** `Authorization: Bearer <token>`

**Parameters:**
- `status` (optional): `INITIATED`, `CREDIT_SUCCESS`, `FAILED`, `REVERSED`
- `page` (default 0): Zero-indexed page
- `size` (default 10, max 100): Page size
- `startDate` (optional): ISO date
- `endDate` (optional): ISO date

**Response (200):**
```json
{
  "content": [
    {
      "txnId": "TXN_abc123def456",
      "fromAccount": "CITI000000000001",
      "toAccount": "CITI000000000002",
      "amount": "5000.00",
      "status": "CREDIT_SUCCESS",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 25,
  "totalPages": 3
}
```

---

## 5. Notification Service (Internal)

No external APIs - consumes Kafka events and sends emails.

---

## 6. API Gateway Routes

| Service | Path Prefix | Routes |
|---------|-------------|--------|
| auth-service | `/auth/**` | All auth endpoints |
| user-service | `/users/**` | Profile, KYC |
| account-service | `/accounts/**` | Accounts, balances, statements |
| transaction-service | `/transaction/**` | Transfers, history |

---

## 7. Rate Limits

| Endpoint Category | Limit | Window |
|-------------------|-------|--------|
| Auth (login/register) | 5 requests | 1 minute |
| Auth (verify/resend) | 3 requests | 5 minutes |
| Business APIs (per user) | 100 requests | 1 minute |
| Admin APIs | 50 requests | 1 minute |
| Statement/History (per user) | 30 requests | 1 minute |

---

## 8. Idempotency

### Usage
Include `Idempotency-Key` header on all mutating requests (POST, PUT, DELETE).

### Key Requirements
- UUID v4 format recommended
- Unique per user per operation
- Server stores key with result for 24 hours
- Duplicate key returns original response

### Example
```bash
curl -X POST https://api.citicore.com/api/v1/accounts/deposit \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{"accNo":"CITI000000000001","amount":"1000.00","txnRef":"DEP_001"}'
```

---

## 9. Pagination

### Request Parameters
| Param | Default | Max | Description |
|-------|---------|-----|-------------|
| `page` | 0 | - | Zero-indexed page number |
| `size` | 10 | 100 | Page size |

### Response Format
```json
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 100,
  "totalPages": 10
}
```

---

## 10. Filtering & Sorting

### Transaction History Filters
| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | enum | `INITIATED`, `CREDIT_SUCCESS`, `FAILED`, `REVERSED` |
| `startDate` | ISO date | Inclusive start |
| `endDate` | ISO date | Inclusive end |
| `type` | enum | `TRANSFER`, `DEPOSIT`, `WITHDRAWAL` |

### Statement Filters
| Parameter | Type | Description |
|-----------|------|-------------|
| `startDate` | ISO date | Inclusive start |
| `endDate` | ISO date | Inclusive end |
| `type` | enum | `DEBIT`, `CREDIT` |

---

## 11. Webhooks (Future)

For real-time notifications, webhooks will be available:
- `transaction.completed`
- `transaction.failed`
- `transaction.reversed`
- `kyc.status_changed`
- `account.created`

---

## 12. SDKs & Examples

### Java/Spring Boot
```java
@FeignClient(name = "citicore-api", url = "${citicore.api.url}")
public interface CiticoreClient {
    @PostMapping("/auth/login")
    AuthResponse login(@RequestBody LoginRequest request);
    
    @PostMapping("/transaction/transfer")
    ApiResponse<String> transfer(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody TransferRequest request
    );
}
```

### cURL Examples
```bash
# Login
TOKEN=$(curl -s -X POST https://api.citicore.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"SecurePass123!"}' | jq -r .accessToken)

# Transfer
curl -X POST https://api.citicore.com/api/v1/transaction/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"CITI000000000001","toAccount":"CITI000000000002","amount":"1000.00","type":"TRANSFER"}'
```

---

## 13. Versioning & Deprecation

- Current version: `v1` (path: `/api/v1/`)
- Breaking changes → new version (`/api/v2/`)
- Deprecation notice: 6 months via `Deprecation` header and developer communications
- Sunset date announced at deprecation

---

## 14. Support

- **API Status:** https://status.citicore.com
- **Documentation:** https://docs.citicore.com
- **Support Email:** api-support@citicore.com
- **Developer Portal:** https://developer.citicore.com
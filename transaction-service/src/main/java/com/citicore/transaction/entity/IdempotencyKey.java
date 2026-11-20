package com.citicore.transaction.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_key_user",
                        columnNames = {"auth_user_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
                @Index(name = "idx_idempotency_user", columnList = "auth_user_id")
        }
)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "auth_user_id",
            nullable = false
    )
    private Long authUserId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            updatable = false
    )
    private String idempotencyKey;

    @Column(name = "txn_ref", nullable = false)
    private String txnRef;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public IdempotencyKey() {
    }

    public Long getId() {
        return id;
    }

    public Long getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(Long authUserId) {
        this.authUserId = authUserId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
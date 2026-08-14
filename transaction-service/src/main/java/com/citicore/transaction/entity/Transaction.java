package com.citicore.transaction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, updatable = false)
    private String txnRef;

    private Long authUserId;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private LocalDateTime createdAt;

    public Transaction() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String txnId;
        private Long authUserId;
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private TransactionType type;
        private TransactionStatus status;
        private LocalDateTime createdAt = LocalDateTime.now();

        public Builder txnId(String txnId) {
            this.txnId = txnId;
            return this;
        }

        public Builder authUserId(Long authUserId) {
            this.authUserId = authUserId;
            return this;
        }

        public Builder fromAccount(String fromAccount) {
            this.fromAccount = fromAccount;
            return this;
        }

        public Builder toAccount(String toAccount) {
            this.toAccount = toAccount;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Transaction build() {
            Transaction t = new Transaction();
            t.txnRef = this.txnId;
            t.authUserId = this.authUserId;
            t.fromAccount = this.fromAccount;
            t.toAccount = this.toAccount;
            t.amount = this.amount;
            t.type = this.type;
            t.status = this.status;
            t.createdAt = this.createdAt;
            return t;
        }


    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public Long getUserId() {
        return authUserId;
    }

    public void setUserId(Long authUserId) {
        this.authUserId = authUserId;
    }

    public String getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(String fromAccount) {
        this.fromAccount = fromAccount;
    }

    public String getToAccount() {
        return toAccount;
    }

    public void setToAccount(String toAccount) {
        this.toAccount = toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
// getters/setters omitted for brevity (keep as needed)
}
package com.citicore.transaction.dto;

import com.citicore.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {

    private String txnId;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime createdAt;

    public TransactionResponse() {}

    private TransactionResponse(Builder builder) {
        this.txnId = builder.txnId;
        this.fromAccount = builder.fromAccount;
        this.toAccount = builder.toAccount;
        this.amount = builder.amount;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String txnId;
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private TransactionStatus status;
        private LocalDateTime createdAt;

        public Builder txnId(String txnId) {
            this.txnId = txnId;
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

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TransactionResponse build() {
            return new TransactionResponse(this);
        }
    }

    public String getTxnId() { return txnId; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

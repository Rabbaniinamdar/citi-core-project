package com.citicore.account.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_statements",
        indexes = {
                @Index(name = "idx_statement_account", columnList = "accountNumber"),
                @Index(name = "idx_statement_date", columnList = "createdAt")
        })

public class AccountStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountNumber;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private BigDecimal amount;

    private BigDecimal balanceAfterTxn;

    @Column(unique = true, nullable = false)
    private String txnRef;

    private String description;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public AccountStatement() {
    }

    public AccountStatement(Builder builder) {
        this.accountNumber = builder.accountNumber;
        this.transactionType = builder.transactionType;
        this.amount = builder.amount;
        this.balanceAfterTxn = builder.balanceAfterTxn;
        this.txnRef = builder.txnRef;
        this.description = builder.description;
        this.createdAt = builder.createdAt;
    }

    public static class Builder {

        private String accountNumber;
        private TransactionType transactionType;
        private BigDecimal amount;
        private BigDecimal balanceAfterTxn;
        private String txnRef;
        private String description;
        private LocalDateTime createdAt;

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder transactionType(TransactionType transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder balanceAfterTxn(BigDecimal balanceAfterTxn) {
            this.balanceAfterTxn = balanceAfterTxn;
            return this;
        }

        public Builder txnRef(String txnRef) {
            this.txnRef = txnRef;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public AccountStatement build() {
            return new AccountStatement(this);
        }
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfterTxn() {
        return balanceAfterTxn;
    }

    public void setBalanceAfterTxn(BigDecimal balanceAfterTxn) {
        this.balanceAfterTxn = balanceAfterTxn;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
package com.citicore.account.dto;

import com.citicore.account.entity.Account;
import com.citicore.account.entity.AccountType;
import com.citicore.account.entity.AccountStatus;

import java.math.BigDecimal;

public class AccountResponse {

    private String accountNumber;
    private Long authUserId;

    private AccountType accountType;
    private AccountStatus status;

    private BigDecimal balance;

    public AccountResponse(Builder builder) {
        this.accountNumber = builder.accountNumber;
        this.authUserId = builder.authUserId;
        this.accountType = builder.accountType;
        this.status = builder.status;
        this.balance = builder.balance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Long getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(Long authUserId) {
        this.authUserId = authUserId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public static AccountResponse from(Account account) {
        return new Builder()
                .accountNumber(account.getAccountNumber())
                .authUserId(account.getAuthUserId())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .build();
    }

    public static class Builder {

        private String accountNumber;
        private Long authUserId;
        private AccountType accountType;
        private AccountStatus status;
        private BigDecimal balance;

        public Builder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public Builder authUserId(Long authUserId) {
            this.authUserId = authUserId;
            return this;
        }

        public Builder accountType(AccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder balance(BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        public Builder status(AccountStatus status) {
            this.status = status;
            return this;
        }

        public AccountResponse build() {
            return new AccountResponse(this);
        }
    }
}
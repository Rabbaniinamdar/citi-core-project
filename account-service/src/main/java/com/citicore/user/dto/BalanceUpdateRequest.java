package com.citicore.user.dto;

import java.math.BigDecimal;

public class BalanceUpdateRequest {

    private String accountNumber;
    private BigDecimal amount;
    private String type;
    private String txnRef;

    public BalanceUpdateRequest(String accountNumber,
                                BigDecimal amount,
                                String type,
                                String txnRef) {
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.type = type;
        this.txnRef = txnRef;
    }

    // Getters and Setters
    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }
}
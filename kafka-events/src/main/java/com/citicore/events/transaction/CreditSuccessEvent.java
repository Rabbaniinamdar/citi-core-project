package com.citicore.events.transaction;

import java.math.BigDecimal;

public class CreditSuccessEvent {

    private String txnRef;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private Long authUserId;

    public CreditSuccessEvent() {
    }
    public CreditSuccessEvent(String txnRef,
                              String fromAccount,
                              String toAccount,
                              BigDecimal amount,
                              Long authUserId) {
        this.txnRef = txnRef;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.authUserId = authUserId;
    }

    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }

    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }

    public Long getAuthUserId() { return authUserId; }
    public void setAuthUserId(Long authUserId) { this.authUserId = authUserId; }
}

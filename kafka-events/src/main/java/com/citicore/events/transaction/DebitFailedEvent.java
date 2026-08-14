package com.citicore.events.transaction;

import java.math.BigDecimal;

public class DebitFailedEvent {

    private String txnRef;
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private String reason;
    private Long authUserId;

    public DebitFailedEvent() {
    }

    public DebitFailedEvent(String txnRef,
                            String fromAccount,
                            String toAccount,
                            BigDecimal amount,
                            String reason,
                            Long authUserId) {
        this.txnRef = txnRef;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.reason = reason;
        this.authUserId = authUserId;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(Long authUserId) {
        this.authUserId = authUserId;
    }
}
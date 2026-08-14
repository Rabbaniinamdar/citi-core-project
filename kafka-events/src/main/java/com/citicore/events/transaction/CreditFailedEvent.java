package com.citicore.events.transaction;

public class CreditFailedEvent {

    private String txnRef;
    private String fromAccount;
    private String toAccount;
    private java.math.BigDecimal amount;
    private String reason;
    private Long authUserId;

    public CreditFailedEvent() {
    }
    public CreditFailedEvent(String txnRef,
                             String fromAccount,
                             String toAccount,
                             java.math.BigDecimal amount,
                             String reason,
                             Long authUserId) {
        this.txnRef = txnRef;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.reason = reason;
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

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getAuthUserId() { return authUserId; }
    public void setAuthUserId(Long authUserId) { this.authUserId = authUserId; }
}

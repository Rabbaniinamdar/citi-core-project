package com.citicore.events.account;
import java.math.BigDecimal;

public class AccountEvent {

    private AccountEventType eventType;

    private String accountNumber;
    private String email;

    private BigDecimal amount;   // null for ACCOUNT_CREATED
    private String txnRef;

    public AccountEvent() {}

    public AccountEvent(AccountEventType eventType,
                        String accountNumber,
                        String email,
                        BigDecimal amount,
                        String txnRef) {
        this.eventType = eventType;
        this.accountNumber = accountNumber;
        this.email = email;
        this.amount = amount;
        this.txnRef = txnRef;
    }

    public AccountEventType getEventType() {
        return eventType;
    }

    public void setEventType(AccountEventType eventType) {
        this.eventType = eventType;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }
}

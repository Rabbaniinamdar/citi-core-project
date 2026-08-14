package com.citicore.transaction.dto;

import com.citicore.transaction.entity.TransactionType;
import java.math.BigDecimal;

public class TransactionRequest {

    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private TransactionType type;

    public TransactionRequest() {}

    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }

    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setType(TransactionType type) { this.type = type; }

    // basic validation helper
    public boolean isInvalid() {
        return
                fromAccount == null ||
                        toAccount == null ||
                        amount == null ||
                        amount.compareTo(BigDecimal.ZERO) <= 0 ||
                        type == null ||
                        fromAccount.equals(toAccount);
    }
}
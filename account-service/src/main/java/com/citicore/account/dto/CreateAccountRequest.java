package com.citicore.account.dto;

import com.citicore.account.entity.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class CreateAccountRequest {

    @NotNull
    private AccountType accountType;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal initialDeposit;

    public CreateAccountRequest(AccountType accountType, BigDecimal initialDeposit) {
        this.accountType = accountType;
        this.initialDeposit = initialDeposit;
    }


    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getInitialDeposit() {
        return initialDeposit;
    }

    public void setInitialDeposit(BigDecimal initialDeposit) {
        this.initialDeposit = initialDeposit;
    }
}
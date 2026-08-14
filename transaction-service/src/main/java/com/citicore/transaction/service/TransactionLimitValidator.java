package com.citicore.transaction.service;

import com.citicore.transaction.entity.TransactionType;
import com.citicore.transaction.exception.DailyLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Validates that a transaction does not exceed the configured daily limit
 * for the given transaction type.
 *
 * Limits are configured in application.yml:
 *   citicore.transaction.daily-limit.TRANSFER: 500000
 *   citicore.transaction.daily-limit.DEPOSIT: 1000000
 *   citicore.transaction.daily-limit.WITHDRAWAL: 200000
 */
@Component
public class TransactionLimitValidator {

    private final Map<String, BigDecimal> dailyLimits;

    public TransactionLimitValidator(
            @Value("${citicore.transaction.daily-limit.TRANSFER:500000}") BigDecimal transferLimit,
            @Value("${citicore.transaction.daily-limit.DEPOSIT:1000000}") BigDecimal depositLimit,
            @Value("${citicore.transaction.daily-limit.WITHDRAWAL:200000}") BigDecimal withdrawalLimit
    ) {
        this.dailyLimits = Map.of(
                "TRANSFER",   transferLimit,
                "DEPOSIT",    depositLimit,
                "WITHDRAWAL", withdrawalLimit
        );
    }

    /**
     * @param type       transaction type (TRANSFER / DEPOSIT / WITHDRAWAL)
     * @param amount     amount of this transaction
     * @param todayTotal sum of all non-failed transactions by this user today
     * @throws DailyLimitExceededException if limit would be breached
     */
    public void validate(TransactionType type, BigDecimal amount, BigDecimal todayTotal) {
        BigDecimal limit = dailyLimits.get(type.name());

        if (limit == null) return; // no limit configured for this type

        BigDecimal projectedTotal = todayTotal.add(amount);

        if (projectedTotal.compareTo(limit) > 0) {
            throw new DailyLimitExceededException(
                    String.format("Daily %s limit of ₹%.2f exceeded. " +
                                    "Today's total would be ₹%.2f.",
                            type.name(), limit, projectedTotal)
            );
        }
    }
}
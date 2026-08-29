package com.citicore.account.config;

import java.lang.annotation.*;

/**
 * Marks a method as read-only — routes its DB operations to
 * the MySQL REPLICA instead of the PRIMARY.
 *
 * Apply to any AccountService method that only reads data:
 *   @ReadOnly
 *   public BigDecimal getBalance(String accNo) { ... }
 *
 * The ReadOnlyDataSourceAspect intercepts these methods,
 * sets DataSourceContextHolder to REPLICA before execution,
 * and clears it after.
 *
 * DO NOT apply to methods that write (debit, credit, deposit,
 * withdraw, createAccount) — those must always use PRIMARY.
 *
 * WHY this approach over @Transactional(readOnly=true)?
 *   Spring's readOnly=true is a hint to Hibernate — it does NOT
 *   route to a different DataSource by itself.
 *   This annotation + AOP actually switches the physical connection.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {
}
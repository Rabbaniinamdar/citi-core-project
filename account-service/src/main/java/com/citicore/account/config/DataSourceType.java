package com.citicore.account.config;

/**
 * Identifies which DataSource to route a DB operation to.
 *
 * PRIMARY → write operations (debit, credit, save, outbox)
 * REPLICA → read operations (getBalance, getMiniStatement,
 *                            getStatement, validateTransfer)
 *
 * Used by DataSourceContextHolder to store the routing decision
 * in a ThreadLocal, and by RoutingDataSource to look it up.
 */
public enum DataSourceType {
    PRIMARY,
    REPLICA
}
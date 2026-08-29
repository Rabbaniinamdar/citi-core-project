package com.citicore.account.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect — intercepts @ReadOnly methods and routes
 * their DB connections to the MySQL REPLICA.
 *
 * Execution order matters when combined with @Transactional:
 *   @Order(1) → this aspect runs BEFORE @Transactional (@Order(2))
 *
 * WHY order matters:
 *   @Transactional opens a DB connection when it starts.
 *   If we set REPLICA AFTER @Transactional opens the connection,
 *   the connection is already bound to PRIMARY — routing has no effect.
 *   By running FIRST (Order 1), we set REPLICA in ThreadLocal
 *   BEFORE @Transactional acquires the connection → correct routing.
 *
 * Flow for a @ReadOnly method:
 *   1. This aspect intercepts the method call (Order 1)
 *   2. Sets DataSourceContextHolder → REPLICA
 *   3. @Transactional starts (Order 2) → acquires REPLICA connection
 *   4. Method executes → reads from REPLICA
 *   5. @Transactional commits/closes connection
 *   6. This aspect's finally block → clears ThreadLocal
 *
 * Flow for a write method (@Transactional, no @ReadOnly):
 *   1. This aspect does NOT intercept (no @ReadOnly annotation)
 *   2. @Transactional starts → DataSourceContextHolder is empty
 *   3. RoutingDataSource.determineCurrentLookupKey() returns PRIMARY (default)
 *   4. Write executes on PRIMARY
 */
@Aspect
@Component
@Order(1) // MUST run before @Transactional (which is Order 2 by default)
public class ReadOnlyDataSourceAspect {

    /**
     * Intercepts any method annotated with @ReadOnly.
     * Sets the DataSource context to REPLICA before execution,
     * clears it in finally block regardless of success or failure.
     */
    @Around("@annotation(com.citicore.account.config.ReadOnly)")
    public Object routeToReplica(
            ProceedingJoinPoint joinPoint) throws Throwable {

        try {
            DataSourceContextHolder
                    .setDataSourceType(DataSourceType.REPLICA);

            System.out.println(
                    "📖 [READ REPLICA] method="
                            + joinPoint.getSignature().getName());

            return joinPoint.proceed();

        } finally {
            DataSourceContextHolder.clear();
        }
    }
    @Around("@annotation(com.citicore.account.config.PrimaryRead)")
    public Object routeToPrimary(ProceedingJoinPoint joinPoint)
            throws Throwable {

        System.out.println(
                "📖 [STRONG READ] method="
                        + joinPoint.getSignature().getName()
        );

        DataSourceContextHolder
                .setDataSourceType(DataSourceType.PRIMARY);

        try {
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
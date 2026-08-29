package com.citicore.account.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Custom DataSource that routes DB connections to either
 * PRIMARY or REPLICA MySQL instance based on the current thread's context.
 *
 * Spring's AbstractRoutingDataSource holds a map of DataSources:
 *   { PRIMARY → primaryDataSource, REPLICA → replicaDataSource }
 *
 * determineCurrentLookupKey() is called by Spring before every
 * DB connection acquisition. It returns the key to look up in
 * that map — PRIMARY or REPLICA.
 *
 * The routing key is stored in DataSourceContextHolder (ThreadLocal)
 * and set by:
 *   1. @ReadOnly AOP aspect  → sets REPLICA before read methods
 *   2. Default (no annotation) → PRIMARY (safe fallback for writes)
 *
 * Registered in DataSourceConfig as the main @Primary DataSource
 * so Spring, JPA, and @Transactional all use it transparently.
 */

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private final ReplicaHealthIndicator replicaHealthIndicator;

    public RoutingDataSource(
            ReplicaHealthIndicator replicaHealthIndicator) {

        this.replicaHealthIndicator = replicaHealthIndicator;
    }

    @Override
    protected Object determineCurrentLookupKey() {

        DataSourceType type =
                DataSourceContextHolder.getDataSourceType();

        /*
         * If the application explicitly requested REPLICA
         * but the replica is currently unhealthy,
         * automatically fall back to PRIMARY.
         */
        if (type == DataSourceType.REPLICA
                && !replicaHealthIndicator.isReplicaAvailable()) {
            System.out.println(
                    "⚠️ [ROUTING] REPLICA unavailable → PRIMARY"
            );
            return DataSourceType.PRIMARY;
        }
        System.out.println(
                "🔀 [ROUTING] DataSource = " + type
                        + " | Thread = " + Thread.currentThread().getName()
        );

        return type;
    }
}
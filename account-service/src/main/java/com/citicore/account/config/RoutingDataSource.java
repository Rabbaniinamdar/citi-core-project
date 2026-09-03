package com.citicore.account.config;

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

        // Safe default: all unspecified operations go to PRIMARY
        if (type == null) {
            System.out.println(
                    "🔀 [ROUTING] No datasource context → PRIMARY"
            );

            return DataSourceType.PRIMARY;
        }

        // Replica requested but unavailable → PRIMARY fallback
        if (type == DataSourceType.REPLICA
                && !replicaHealthIndicator.isReplicaAvailable()) {

            System.out.println(
                    "⚠️ [ROUTING] REPLICA unavailable → PRIMARY"
            );

            return DataSourceType.PRIMARY;
        }

        System.out.println(
                "🔀 [ROUTING] DataSource = "
                        + type
                        + " | Thread = "
                        + Thread.currentThread().getName()
        );

        return type;
    }
}
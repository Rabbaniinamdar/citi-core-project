package com.citicore.account.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReplicaHealthScheduler {

    private final JdbcTemplate replicaJdbcTemplate;
    private final ReplicaHealthIndicator replicaHealthIndicator;

    public ReplicaHealthScheduler(
            @Qualifier("replicaJdbcTemplate")
            JdbcTemplate replicaJdbcTemplate,
            ReplicaHealthIndicator replicaHealthIndicator) {

        this.replicaJdbcTemplate = replicaJdbcTemplate;
        this.replicaHealthIndicator = replicaHealthIndicator;
    }

    @Scheduled(fixedDelay = 10_000)
    public void checkReplicaHealth() {

        try {

            replicaJdbcTemplate.queryForObject(
                    "SELECT 1",
                    Integer.class
            );
            System.out.println(
                    "❤️ [REPLICA HEALTH] SELECT 1 successful"
            );

            replicaHealthIndicator.markReplicaAvailable();

        } catch (Exception ex) {

            replicaHealthIndicator.markReplicaUnavailable();

            System.err.println(
                    "⚠️ [REPLICA HEALTH] Replica unavailable: "
                            + ex.getMessage()
            );
        }
    }
}
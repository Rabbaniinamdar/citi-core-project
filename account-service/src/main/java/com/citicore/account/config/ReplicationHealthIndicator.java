package com.citicore.account.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Custom Spring Actuator health indicator for MySQL replication.
 *
 * Exposed at: GET /actuator/health
 * Shows replication status and lag in the health response.
 *
 * Healthy response example:
 * {
 *   "status": "UP",
 *   "components": {
 *     "mysqlReplication": {
 *       "status": "UP",
 *       "details": {
 *         "replica_io_running": "Yes",
 *         "replica_sql_running": "Yes",
 *         "seconds_behind_source": 0,
 *         "source_host": "mysql-primary",
 *         "replica_host": "mysql-replica"
 *       }
 *     }
 *   }
 * }
 *
 * Unhealthy when:
 *   - Replica_IO_Running  = No  (cannot connect to primary)
 *   - Replica_SQL_Running = No  (SQL thread crashed)
 *   - Seconds_Behind_Source > 30  (replica is lagging > 30s)
 *
 * Used by:
 *   - Kubernetes readiness probe → stops routing to this pod if replica is lagging
 *   - Monitoring alerts → PagerDuty/Slack notification on replication failure
 */
@Component("mysqlReplication")
public class ReplicationHealthIndicator implements HealthIndicator {

    private final DataSource replicaDataSource;

    private static final int MAX_ACCEPTABLE_LAG_SECONDS = 30;

    public ReplicationHealthIndicator(
            @Qualifier("replicaDataSource") DataSource replicaDataSource) {
        this.replicaDataSource = replicaDataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = replicaDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery("SHOW REPLICA STATUS")) {

            if (!rs.next()) {
                // SHOW REPLICA STATUS returns no rows if replication
                // is not configured — this is a config problem
                return Health.down()
                        .withDetail("error", "Replication not configured on replica")
                        .build();
            }

            String ioRunning  = rs.getString("Replica_IO_Running");
            String sqlRunning = rs.getString("Replica_SQL_Running");
            Object lagObj     = rs.getObject("Seconds_Behind_Source");
            String sourceHost = rs.getString("Source_Host");
            String lastError  = rs.getString("Last_Error");

            int lagSeconds = (lagObj != null)
                    ? Integer.parseInt(lagObj.toString())
                    : -1;

            Health.Builder builder = Health.up()
                    .withDetail("replica_io_running",    ioRunning)
                    .withDetail("replica_sql_running",   sqlRunning)
                    .withDetail("seconds_behind_source", lagSeconds)
                    .withDetail("source_host",           sourceHost);

            // ── IO thread not running → cannot read from primary ──────────
            if (!"Yes".equalsIgnoreCase(ioRunning)) {
                return Health.down()
                        .withDetail("replica_io_running",    ioRunning)
                        .withDetail("replica_sql_running",   sqlRunning)
                        .withDetail("seconds_behind_source", lagSeconds)
                        .withDetail("source_host",           sourceHost)
                        .withDetail("last_error",            lastError)
                        .withDetail("error", "Replica IO thread is not running — "
                                + "replica cannot connect to primary")
                        .build();
            }

            // ── SQL thread not running → not applying events ───────────────
            if (!"Yes".equalsIgnoreCase(sqlRunning)) {
                return Health.down()
                        .withDetail("replica_io_running",    ioRunning)
                        .withDetail("replica_sql_running",   sqlRunning)
                        .withDetail("seconds_behind_source", lagSeconds)
                        .withDetail("source_host",           sourceHost)
                        .withDetail("last_error",            lastError)
                        .withDetail("error", "Replica SQL thread is not running — "
                                + "events are not being applied")
                        .build();
            }

            // ── Lag too high → reads might return stale data ──────────────
            if (lagSeconds > MAX_ACCEPTABLE_LAG_SECONDS) {
                return Health.down()
                        .withDetail("replica_io_running",    ioRunning)
                        .withDetail("replica_sql_running",   sqlRunning)
                        .withDetail("seconds_behind_source", lagSeconds)
                        .withDetail("source_host",           sourceHost)
                        .withDetail("error", "Replica lag (" + lagSeconds
                                + "s) exceeds threshold (" + MAX_ACCEPTABLE_LAG_SECONDS + "s)")
                        .build();
            }

            return builder.build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Cannot connect to replica: " + e.getMessage())
                    .build();
        }
    }
}
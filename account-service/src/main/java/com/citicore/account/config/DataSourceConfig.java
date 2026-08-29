package com.citicore.account.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.primary.jdbc-url}")
    private String primaryUrl;

    @Value("${spring.datasource.primary.username}")
    private String primaryUsername;

    @Value("${spring.datasource.primary.password}")
    private String primaryPassword;

    @Value("${spring.datasource.replica.jdbc-url}")
    private String replicaUrl;

    @Value("${spring.datasource.replica.username}")
    private String replicaUsername;

    @Value("${spring.datasource.replica.password}")
    private String replicaPassword;

    // ─────────────────────────────────────────────────────────────────────────
    // DATA SOURCES
    // ─────────────────────────────────────────────────────────────────────────

    @Bean("primaryDataSource")
    public DataSource primaryDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(primaryUrl);
        ds.setUsername(primaryUsername);
        ds.setPassword(primaryPassword);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(3);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("CitiCore-Primary-Pool");
        ds.setConnectionTestQuery("SELECT 1");
        ds.setKeepaliveTime(60_000);
        return ds;
    }

    @Bean("replicaDataSource")
    public DataSource replicaDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(replicaUrl);
        ds.setUsername(replicaUsername);
        ds.setPassword(replicaPassword);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("CitiCore-Replica-Pool");
        ds.setConnectionTestQuery("SELECT 1");
        ds.setKeepaliveTime(60_000);
        return ds;
    }

    @Primary
    @Bean("routingDataSource")
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            @Qualifier("replicaDataSource") DataSource replicaDataSource,
            ReplicaHealthIndicator replicaHealthIndicator) {

        RoutingDataSource routingDataSource =
                new RoutingDataSource(replicaHealthIndicator);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primaryDataSource);
        targetDataSources.put(DataSourceType.REPLICA, replicaDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);

        return routingDataSource;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JDBC TEMPLATES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ✅ This bean was missing — ReplicaHealthScheduler needs it.
     * Uses replicaDataSource directly (not the routing datasource)
     * so health checks always hit the actual replica, not primary.
     */
    @Bean("replicaJdbcTemplate")
    public JdbcTemplate replicaJdbcTemplate(
            @Qualifier("replicaDataSource") DataSource replicaDataSource) {
        return new JdbcTemplate(replicaDataSource);
    }

    /**
     * Primary JdbcTemplate — available if needed for raw SQL on primary.
     */
    @Bean("primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(
            @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }
}
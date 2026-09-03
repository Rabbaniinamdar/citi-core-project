package com.citicore.account.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component("mysqlReplication")
public class ReplicationHealthIndicator implements HealthIndicator {

    private final DataSource replicaDataSource;

    public ReplicationHealthIndicator(
            @Qualifier("replicaDataSource") DataSource replicaDataSource) {
        this.replicaDataSource = replicaDataSource;
    }

    @Override
    public Health health() {

        try (
                Connection connection = replicaDataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1");
                ResultSet resultSet = statement.executeQuery()
        ) {

            if (resultSet.next() && resultSet.getInt(1) == 1) {

                return Health.up()
                        .withDetail("database", "MySQL Replica")
                        .withDetail("connection", "UP")
                        .withDetail("validationQuery", "SELECT 1")
                        .build();
            }

            return Health.down()
                    .withDetail("error", "Replica validation query returned no result")
                    .build();

        } catch (Exception e) {

            return Health.down()
                    .withDetail(
                            "error",
                            "Cannot connect to replica: " + e.getMessage()
                    )
                    .build();
        }
    }
}
package com.citicore.account.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Automated MySQL partition maintenance service.
 *
 * Runs two scheduled jobs:
 *
 * 1. ADD PARTITION (runs 1st of every month at midnight)
 *    Adds next month's partition to account_statements and account_outbox.
 *    This ensures the p_future catch-all partition never fills up
 *    and new data always lands in a named monthly partition.
 *
 * 2. DROP OLD PARTITIONS (runs 1st of every month at 01:00)
 *    Drops partitions older than 36 months (3 years) for data retention.
 *    DROP PARTITION is instant (no row-by-row DELETE scan) — it
 *    simply removes the partition file from disk.
 *
 * WHY automate this?
 *    Without automation, you must manually add a partition every month.
 *    If you forget, new data goes into p_future (unpartitioned catch-all)
 *    and partition pruning stops working — defeating the entire purpose.
 *
 * CRON: "0 0 1 * *" = at 00:00 on the 1st day of every month
 *       "0 1 1 * *" = at 01:00 on the 1st day of every month
 */
@Service
public class PartitionMaintenanceService {

    @PersistenceContext
    private EntityManager entityManager;

    // ─────────────────────────────────────────────────────────────────────────
    // ADD NEXT MONTH'S PARTITION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs on the 1st of every month at midnight.
     * Adds the partition for the MONTH AFTER next (2 months ahead)
     * to ensure we always have a buffer — if this job fails one month,
     * next month's data still has a partition to land in.
     * <p>
     * Example: runs on 2026-07-01 → adds partition for 2026-09-01
     * (p_2026_09 covers 2026-09-01 to 2026-10-01)
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void addNextPartition() {
        // Add 2 months ahead for safety buffer
        LocalDate nextMonth = LocalDate.now().plusMonths(1);
        LocalDate twoMonths = LocalDate.now().plusMonths(2);
        LocalDate threeMonths = LocalDate.now().plusMonths(3);

        addPartitionToTable("account_statements", twoMonths, threeMonths);
        addPartitionToTable("account_outbox", twoMonths, threeMonths);

        System.out.println("✅ [PARTITION] Added partitions for "
                + twoMonths.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    /**
     * Reorganizes p_future to split off a new named monthly partition.
     * <p>
     * MySQL requires ALTER TABLE ... REORGANIZE PARTITION to split
     * the catch-all p_future into a named partition + new p_future.
     * This is the correct way — cannot use ADD PARTITION when
     * p_future (MAXVALUE) already exists.
     *
     * @param tableName      "account_statements" or "account_outbox"
     * @param partitionStart first day of the new partition's month
     * @param partitionEnd   first day of the NEXT month (exclusive upper bound)
     */
    private void addPartitionToTable(String tableName,
                                     LocalDate partitionStart,
                                     LocalDate partitionEnd) {
        String partitionName = "p_"
                + partitionStart.format(DateTimeFormatter.ofPattern("yyyy_MM"));

        String unixEnd = "UNIX_TIMESTAMP('"
                + partitionEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + "')";

        String sql = String.format("""
                ALTER TABLE %s
                REORGANIZE PARTITION p_future INTO (
                    PARTITION %s VALUES LESS THAN (%s),
                    PARTITION p_future VALUES LESS THAN MAXVALUE
                )
                """, tableName, partitionName, unixEnd);

        try {
            entityManager.createNativeQuery(sql).executeUpdate();
            System.out.println("✅ [PARTITION ADDED] table=" + tableName
                    + " | partition=" + partitionName
                    + " | upperBound=" + partitionEnd);
        } catch (Exception e) {
            // Partition already exists — safe to ignore on duplicate run
            if (e.getMessage() != null
                    && e.getMessage().contains("Duplicate partition name")) {
                System.out.println("⚠️ [PARTITION] Already exists: "
                        + partitionName + " on " + tableName + " — skipping");
            } else {
                System.out.println("❌ [PARTITION ERROR] table=" + tableName
                        + " | error=" + e.getMessage());
                throw new RuntimeException("Failed to add partition: "
                        + partitionName, e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DROP OLD PARTITIONS (data retention)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs on the 1st of every month at 01:00.
     * Drops partitions older than 36 months (3 years).
     * <p>
     * Data retention policy:
     * account_statements → keep 36 months (regulatory requirement)
     * account_outbox     → keep 3 months  (operational data only)
     * <p>
     * DROP PARTITION is INSTANT — it removes the partition data file
     * directly from disk without scanning rows.
     * A DELETE WHERE created_at < '...' on 1B rows could take hours.
     * DROP PARTITION on the same data takes milliseconds.
     */
    @Scheduled(cron = "0 1 1 * * *")
    @Transactional
    public void dropOldPartitions() {
        // Statements: keep 36 months
        LocalDate statementsRetentionDate = LocalDate.now().minusMonths(36);
        dropOldPartition("account_statements", statementsRetentionDate);

        // Outbox: keep 3 months (old SENT/FAILED records are just audit logs)
        LocalDate outboxRetentionDate = LocalDate.now().minusMonths(3);
        dropOldPartition("account_outbox", outboxRetentionDate);
    }

    /**
     * Drops a specific monthly partition if it exists.
     *
     * @param tableName     table to drop partition from
     * @param retentionDate drop the partition for this month
     */
    private void dropOldPartition(String tableName, LocalDate retentionDate) {
        String partitionName = "p_"
                + retentionDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));

        // Check if partition exists before trying to drop
        String checkSql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.PARTITIONS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME   = ?
                  AND PARTITION_NAME = ?
                """;

        Number count = (Number) entityManager
                .createNativeQuery(checkSql)
                .setParameter(1, tableName)
                .setParameter(2, partitionName)
                .getSingleResult();

        if (count.intValue() == 0) {
            System.out.println("⚠️ [PARTITION DROP] Not found: "
                    + partitionName + " on " + tableName + " — skipping");
            return;
        }

        String dropSql = String.format(
                "ALTER TABLE %s DROP PARTITION %s",
                tableName, partitionName);

        try {
            entityManager.createNativeQuery(dropSql).executeUpdate();
            System.out.println("🗑️ [PARTITION DROPPED] table=" + tableName
                    + " | partition=" + partitionName);
        } catch (Exception e) {
            System.out.println("❌ [PARTITION DROP ERROR] table=" + tableName
                    + " | partition=" + partitionName
                    + " | error=" + e.getMessage());
            // Don't rethrow — dropping old partitions is best-effort.
            // A failed drop just means old data stays longer.
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HEALTH CHECK — check partition status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs partition sizes for account_statements.
     * Runs every Sunday at 02:00 — useful for monitoring growth.
     *
     * In production, push these metrics to your monitoring system
     * (Prometheus, CloudWatch, Datadog) instead of just logging.
     */
    /**
     * Logs partition sizes for account_statements.
     * Runs every Sunday at 02:00.
     */
    @Scheduled(cron = "0 2 * * * SUN")
    public void logPartitionHealth() {

        String sql = """
                SELECT
                    PARTITION_NAME,
                    TABLE_ROWS,
                    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
                    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
                FROM INFORMATION_SCHEMA.PARTITIONS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'account_statements'
                  AND PARTITION_NAME IS NOT NULL
                ORDER BY PARTITION_NAME
                """;

        try {

            @SuppressWarnings("unchecked")
            java.util.List<Object[]> results =
                    entityManager
                            .createNativeQuery(sql)
                            .getResultList();

            System.out.println(
                    "📊 [PARTITION HEALTH] account_statements:"
            );

            for (Object[] row : results) {

                System.out.printf(
                        "   %-15s rows=%-12s data=%-8s MB index=%-8s MB%n",
                        row[0],
                        row[1],
                        row[2],
                        row[3]
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "❌ [PARTITION HEALTH] Failed: "
                            + e.getMessage()
            );
        }
    }
}
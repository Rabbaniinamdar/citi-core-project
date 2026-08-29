package com.citicore.account.config;

/**
 * ThreadLocal holder for the current DataSource routing key.
 *
 * Each HTTP request runs in its own thread.
 * This class stores which DataSource (PRIMARY or REPLICA) that
 * thread should use — set before the DB call, cleared after.
 *
 * Flow:
 *   @ReadOnlyOperation sets REPLICA  → DB call → clear
 *   @Transactional (write) uses PRIMARY by default
 *
 * ThreadLocal is inherently thread-safe — each thread has its
 * own isolated copy of the value.
 *
 * CRITICAL: always call clear() in a finally block to prevent
 * memory leaks in thread-pool environments (Tomcat reuses threads).
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT =
            new ThreadLocal<>();

    /** Set the current thread to use the PRIMARY (write) datasource. */
    public static void setPrimary() {
        CONTEXT.set(DataSourceType.PRIMARY);
    }

    /** Set the current thread to use the REPLICA (read) datasource. */
    public static void setReplica() {
        CONTEXT.set(DataSourceType.REPLICA);
    }

    public static void setDataSourceType(DataSourceType type) {
        CONTEXT.set(type);
    }
    /**
     * Returns the current routing key for this thread.
     * Returns PRIMARY if nothing has been set (safe default).
     */
    public static DataSourceType getDataSourceType() {
        DataSourceType type = CONTEXT.get();
        return type != null ? type : DataSourceType.PRIMARY;
    }

    /**
     * Clears the ThreadLocal value.
     * MUST be called after every DB operation to prevent
     * incorrect routing on the next request that reuses this thread.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
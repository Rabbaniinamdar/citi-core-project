package com.citicore.account.config;

public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT =
            new ThreadLocal<>();

    public static void setPrimary() {
        CONTEXT.set(DataSourceType.PRIMARY);
    }

    public static void setReplica() {
        CONTEXT.set(DataSourceType.REPLICA);
    }

    public static void setDataSourceType(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static DataSourceType getDataSourceType() {
        DataSourceType type = CONTEXT.get();
        return type != null
                ? type
                : DataSourceType.PRIMARY;
    }

    public static DataSourceType getCurrentDataSourceType() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
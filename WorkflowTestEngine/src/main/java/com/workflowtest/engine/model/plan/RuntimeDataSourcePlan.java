package com.workflowtest.engine.model.plan;

/**
 * 执行计划中的 JDBC 数据源定义。
 */
public record RuntimeDataSourcePlan(
        long id,
        String driverClass,
        String jdbcUrl,
        String username,
        String password,
        boolean allowDangerousSql) {
}

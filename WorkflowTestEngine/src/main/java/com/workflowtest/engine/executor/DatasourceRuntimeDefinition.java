package com.workflowtest.engine.executor;

public record DatasourceRuntimeDefinition(Long id, String driverClass, String jdbcUrl, String username,
                                          String encryptedPassword, boolean allowDangerousSql) {
}

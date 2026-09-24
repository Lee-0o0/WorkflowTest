package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;

import java.util.Locale;

public record SqlStepConfig(
        long datasourceId,
        SqlOperation operation,
        String sql,
        int timeoutSeconds,
        int maxRows
) implements StepConfig {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_ROWS = 100;

    public enum SqlOperation {
        QUERY, UPDATE;

        public static SqlOperation from(String raw) {
            String normalized = raw == null || raw.isBlank() ? "QUERY" : raw.toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "QUERY" -> QUERY;
                case "UPDATE" -> UPDATE;
                default -> throw new IllegalArgumentException(String.format(EngineMessages.SQL_OPERATION_UNSUPPORTED, normalized));
            };
        }
    }

    public static SqlStepConfig from(JsonNode node, ObjectMapper mapper) {
        JsonNode datasourceNode = node.path("datasourceId");
        if (datasourceNode.isMissingNode() || datasourceNode.isNull()) {
            throw new IllegalArgumentException(EngineMessages.SQL_DATASOURCE_REQUIRED);
        }
        long datasourceId = datasourceNode.isNumber() ? datasourceNode.asLong() : Long.parseLong(datasourceNode.asText());
        String sql = node.path("sql").asText();
        if (datasourceId <= CommonConstant.ZERO || sql.isBlank()) {
            throw new IllegalArgumentException(EngineMessages.SQL_DATASOURCE_REQUIRED);
        }
        return new SqlStepConfig(
                datasourceId,
                SqlOperation.from(node.path("operation").asText("QUERY")),
                sql,
                node.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS),
                node.path("maxRows").asInt(DEFAULT_MAX_ROWS));
    }
}

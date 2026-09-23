package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.executor.support.DatasourceRuntimeDefinition;
import com.workflowtest.engine.executor.support.NamedSql;
import com.workflowtest.engine.executor.support.RuntimeDataSourceManager;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.runtime.StepResult.OutputField;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SqlStepExecutor implements StepExecutor {
    private static final class ConfigField {
        static final String DATASOURCE_ID = "datasourceId";
        static final String OPERATION = "operation";
        static final String SQL = "sql";
        static final String PARAMETERS = "parameters";
        static final String TIMEOUT_SECONDS = "timeoutSeconds";
        static final String MAX_ROWS = "maxRows";
        static final String OPERATION_QUERY = "QUERY";
        static final String OPERATION_UPDATE = "UPDATE";
        static final int DEFAULT_TIMEOUT_SECONDS = 10;
        static final int DEFAULT_MAX_ROWS = 100;
    }

    private final RuntimeDataSourceManager dataSources;
    private final ObjectMapper objectMapper;

    @Override public StepType supports() {
        return StepType.SQL;
    }

    @Override
    public StepResult execute(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        long started = System.nanoTime();
        var datasourceNode = config.path(ConfigField.DATASOURCE_ID);
        if (datasourceNode.isMissingNode() || datasourceNode.isNull()) {
            throw new IllegalArgumentException(EngineMessages.SQL_DATASOURCE_REQUIRED);
        }
        Long dataSourceId = datasourceNode.isNumber() ? datasourceNode.asLong() : Long.parseLong(datasourceNode.asText());
        String operation = config.path(ConfigField.OPERATION).asText(ConfigField.OPERATION_QUERY).toUpperCase(Locale.ROOT);
        String sql = config.path(ConfigField.SQL).asText();
        if (dataSourceId <= CommonConstant.ZERO || sql.isBlank()) {
            throw new IllegalArgumentException(EngineMessages.SQL_DATASOURCE_REQUIRED);
        }
        DatasourceRuntimeDefinition definition = dataSources.definition(dataSourceId);
        rejectDangerous(sql, definition.allowDangerousSql());
        Map<String, Object> params = config.has(ConfigField.PARAMETERS)
                ? objectMapper.convertValue(config.get(ConfigField.PARAMETERS), Map.class) : Map.of();
        NamedSql.Parsed parsed = NamedSql.parse(sql, params);
        ObjectNode output = objectMapper.createObjectNode();
        ObjectNode request = objectMapper.createObjectNode();
        request.put(ConfigField.DATASOURCE_ID, String.valueOf(dataSourceId));
        request.put(ConfigField.OPERATION, operation);
        request.put(ConfigField.SQL, sql);
        request.set(ConfigField.PARAMETERS, objectMapper.valueToTree(params));
        try (var connection = dataSources.get(dataSourceId).getConnection();
             var statement = connection.prepareStatement(parsed.sql())) {
            statement.setQueryTimeout(config.path(ConfigField.TIMEOUT_SECONDS).asInt(ConfigField.DEFAULT_TIMEOUT_SECONDS));
            for (int i = CommonConstant.ZERO; i < parsed.values().size(); i++) {
                statement.setObject(i + CommonConstant.ONE, parsed.values().get(i));
            }
            if (ConfigField.OPERATION_QUERY.equals(operation)) {
                int maxRows = config.path(ConfigField.MAX_ROWS).asInt(ConfigField.DEFAULT_MAX_ROWS);
                statement.setMaxRows(maxRows);
                try (ResultSet rs = statement.executeQuery()) {
                    ArrayNode rows = objectMapper.createArrayNode();
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        ObjectNode row = objectMapper.createObjectNode();
                        for (int col = CommonConstant.ONE; col <= md.getColumnCount(); col++) {
                            row.set(md.getColumnLabel(col), objectMapper.valueToTree(rs.getObject(col)));
                        }
                        rows.add(row);
                    }
                    output.set(OutputField.ROWS, rows);
                    output.put(OutputField.ROW_COUNT, rows.size());
                }
            } else if (ConfigField.OPERATION_UPDATE.equals(operation)) {
                output.put(OutputField.UPDATE_COUNT, statement.executeUpdate());
            } else {
                throw new IllegalArgumentException(String.format(EngineMessages.SQL_OPERATION_UNSUPPORTED, operation));
            }
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put(OutputField.ELAPSED_MS, elapsed);
        return new StepResult(output, request, output.deepCopy(), elapsed);
    }

    private void rejectDangerous(String sql, boolean allowed) {
        if (allowed) return;
        String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.matches("^(DROP|TRUNCATE|ALTER|CREATE)\\b.*")) {
            throw new IllegalArgumentException(EngineMessages.SQL_DANGEROUS_FORBIDDEN);
        }
    }
}

package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.executor.config.SqlStepConfig;
import com.workflowtest.engine.executor.support.DatasourceRuntimeDefinition;
import com.workflowtest.engine.executor.support.NamedSql;
import com.workflowtest.engine.executor.support.RuntimeDataSourceManager;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.runtime.StepResult.OutputField;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.Locale;

@RequiredArgsConstructor
public class SqlStepExecutor extends TypedStepExecutor<SqlStepConfig> {
    private static final class Field {
        static final String DATASOURCE_ID = "datasourceId";
        static final String OPERATION = "operation";
        static final String SQL = "sql";
    }

    private final RuntimeDataSourceManager dataSources;
    private final ObjectMapper objectMapper;

    @Override
    protected Class<SqlStepConfig> configType() {
        return SqlStepConfig.class;
    }

    @Override
    public StepType supports() {
        return StepType.SQL;
    }

    @Override
    protected StepResult doExecute(RuntimeStep step, SqlStepConfig config, ExecutionContext context,
                                   RuntimeVariableScope variableScope) throws Exception {
        long started = System.nanoTime();
        DatasourceRuntimeDefinition definition = dataSources.definition(config.datasourceId());
        rejectDangerous(config.sql(), definition.allowDangerousSql());
        NamedSql.Parsed parsed = NamedSql.parse(config.sql(), context);
        ObjectNode output = objectMapper.createObjectNode();
        ObjectNode request = objectMapper.createObjectNode();
        request.put(Field.DATASOURCE_ID, String.valueOf(config.datasourceId()));
        request.put(Field.OPERATION, config.operation().name());
        request.put(Field.SQL, config.sql());
        try (var connection = dataSources.get(config.datasourceId()).getConnection();
             var statement = connection.prepareStatement(parsed.sql())) {
            statement.setQueryTimeout(config.timeoutSeconds());
            for (int i = CommonConstant.ZERO; i < parsed.values().size(); i++) {
                statement.setObject(i + CommonConstant.ONE, parsed.values().get(i));
            }
            switch (config.operation()) {
                case QUERY -> executeQuery(statement, config.maxRows(), output);
                case UPDATE -> output.put(OutputField.UPDATE_COUNT, statement.executeUpdate());
            }
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put(OutputField.ELAPSED_MS, elapsed);
        return new StepResult(output, request, output.deepCopy(), elapsed);
    }

    private void executeQuery(java.sql.PreparedStatement statement, int maxRows, ObjectNode output) throws Exception {
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
    }

    private void rejectDangerous(String sql, boolean allowed) {
        if (allowed) {
            return;
        }
        String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.matches("^(DROP|TRUNCATE|ALTER|CREATE)\\b.*")) {
            throw new IllegalArgumentException(EngineMessages.SQL_DANGEROUS_FORBIDDEN);
        }
    }
}

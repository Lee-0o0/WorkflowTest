package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;

import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepExecutor;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SqlStepExecutor implements StepExecutor {
    private final RuntimeDataSourceManager dataSources;
    private final ObjectMapper objectMapper;
    @Override public StepType supports() { return StepType.SQL; }

    @Override
    public StepResult execute(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        long started = System.nanoTime();
        var datasourceNode = config.path("datasourceId");
        if (datasourceNode.isMissingNode() || datasourceNode.isNull()) {
            throw new IllegalArgumentException("SQL 数据源和语句不能为空");
        }
        Long dataSourceId = datasourceNode.isNumber() ? datasourceNode.asLong() : Long.parseLong(datasourceNode.asText());
        String operation = config.path("operation").asText("QUERY").toUpperCase(Locale.ROOT);
        String sql = config.path("sql").asText();
        if (dataSourceId <= 0 || sql.isBlank()) throw new IllegalArgumentException("SQL 数据源和语句不能为空");
        DatasourceRuntimeDefinition definition = dataSources.definition(dataSourceId);
        rejectDangerous(sql, definition.allowDangerousSql());
        Map<String, Object> params = config.has("parameters")
                ? objectMapper.convertValue(config.get("parameters"), Map.class) : Map.of();
        NamedSql.Parsed parsed = NamedSql.parse(sql, params);
        ObjectNode output = objectMapper.createObjectNode();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("datasourceId", String.valueOf(dataSourceId)); request.put("operation", operation); request.put("sql", sql);
        request.set("parameters", objectMapper.valueToTree(params));
        try (var connection = dataSources.get(dataSourceId).getConnection();
             var statement = connection.prepareStatement(parsed.sql())) {
            statement.setQueryTimeout(config.path("timeoutSeconds").asInt(10));
            for (int i = 0; i < parsed.values().size(); i++) statement.setObject(i + 1, parsed.values().get(i));
            if ("QUERY".equals(operation)) {
                int maxRows = config.path("maxRows").asInt(100);
                statement.setMaxRows(maxRows);
                try (ResultSet rs = statement.executeQuery()) {
                    ArrayNode rows = objectMapper.createArrayNode();
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        ObjectNode row = objectMapper.createObjectNode();
                        for (int col = 1; col <= md.getColumnCount(); col++) {
                            row.set(md.getColumnLabel(col), objectMapper.valueToTree(rs.getObject(col)));
                        }
                        rows.add(row);
                    }
                    output.set("rows", rows); output.put("rowCount", rows.size());
                }
            } else if ("UPDATE".equals(operation)) {
                output.put("updateCount", statement.executeUpdate());
            } else throw new IllegalArgumentException("不支持的 SQL 操作: " + operation);
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put("elapsedMs", elapsed);
        return new StepResult(output, request, output.deepCopy(), elapsed);
    }

    private void rejectDangerous(String sql, boolean allowed) {
        if (allowed) return;
        String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.matches("^(DROP|TRUNCATE|ALTER|CREATE)\\b.*"))
            throw new IllegalArgumentException("危险 SQL 默认禁止执行");
    }
}

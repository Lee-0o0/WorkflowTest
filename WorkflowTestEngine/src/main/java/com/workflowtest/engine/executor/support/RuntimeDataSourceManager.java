package com.workflowtest.engine.executor.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.plan.RuntimeDataSourcePlan;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行期数据源注册表：由 Server 通过执行计划 {@code resources.datasources} 传入，Engine 运行时合并注册。
 */
@RequiredArgsConstructor
public class RuntimeDataSourceManager {
    private final ObjectMapper objectMapper;
    private final Map<Long, com.zaxxer.hikari.HikariDataSource> pools = new ConcurrentHashMap<>();
    private volatile Map<Long, DatasourceRuntimeDefinition> definitions = Map.of();

    /** 合并执行计划中的数据源定义（同 id 后者覆盖前者）。 */
    public void merge(List<RuntimeDataSourcePlan> datasources) {
        if (datasources == null || datasources.isEmpty()) {
            return;
        }
        Map<Long, DatasourceRuntimeDefinition> next = new ConcurrentHashMap<>(definitions);
        for (RuntimeDataSourcePlan source : datasources) {
            if (source.id() <= 0) {
                continue;
            }
            next.put(source.id(), new DatasourceRuntimeDefinition(
                    source.id(),
                    source.driverClass(),
                    source.jdbcUrl(),
                    source.username(),
                    source.password(),
                    source.allowDangerousSql()));
        }
        definitions = Map.copyOf(next);
    }

    /** @deprecated 保留 JSON 合并仅供过渡；新代码请使用 {@link #merge(List)}。 */
    @Deprecated
    public void merge(JsonNode datasources) {
        if (datasources == null || !datasources.isArray() || datasources.isEmpty()) {
            return;
        }
        Map<Long, DatasourceRuntimeDefinition> next = new ConcurrentHashMap<>(definitions);
        for (JsonNode node : datasources) {
            long id = node.path("id").asLong();
            if (id <= 0) {
                continue;
            }
            Map<String, Object> config = objectMapper.convertValue(node, Map.class);
            next.put(id, new DatasourceRuntimeDefinition(
                    id,
                    string(config, "driverClass"),
                    JdbcConnectionConfig.resolveJdbcUrl(config),
                    string(config, "username"),
                    string(config, "password"),
                    bool(config, "allowDangerousSql")));
        }
        definitions = Map.copyOf(next);
    }

    public void clear() {
        closePools();
        definitions = Map.of();
    }

    public DataSource get(Long id) {
        return pools.computeIfAbsent(id, this::create);
    }

    public DatasourceRuntimeDefinition definition(Long id) {
        DatasourceRuntimeDefinition source = definitions.get(id);
        if (source == null) {
            throw new IllegalArgumentException("执行计划中未找到数据源: " + id);
        }
        return source;
    }

    private com.zaxxer.hikari.HikariDataSource create(Long id) {
        DatasourceRuntimeDefinition source = definition(id);
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setPoolName("workflow-runtime-" + id);
        config.setDriverClassName(source.driverClass());
        config.setJdbcUrl(source.jdbcUrl());
        config.setUsername(source.username());
        config.setPassword(source.password());
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(5000);
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    private void closePools() {
        pools.values().forEach(com.zaxxer.hikari.HikariDataSource::close);
        pools.clear();
    }

    private static String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
}

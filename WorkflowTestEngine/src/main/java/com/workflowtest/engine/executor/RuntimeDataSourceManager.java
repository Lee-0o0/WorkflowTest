package com.workflowtest.engine.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResourceType;
import com.workflowtest.engine.persistence.entity.ProjectResourceEntity;
import com.workflowtest.engine.persistence.mapper.ProjectResourceMapper;
import com.workflowtest.engine.security.SecretCipher;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RuntimeDataSourceManager {
    private final ProjectResourceMapper mapper;
    private final SecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final Map<Long, com.zaxxer.hikari.HikariDataSource> pools = new ConcurrentHashMap<>();

    public DataSource get(Long id) {
        return pools.computeIfAbsent(id, this::create);
    }

    public DatasourceRuntimeDefinition definition(Long id) {
        ProjectResourceEntity entity = requireDatasource(id);
        Map<String, Object> config = readConfig(entity.getConfigJson());
        return new DatasourceRuntimeDefinition(
                entity.getId(),
                string(config, "driverClass"),
                JdbcConnectionConfig.resolveJdbcUrl(config),
                string(config, "username"),
                string(config, "encryptedPassword"),
                bool(config, "allowDangerousSql"));
    }

    private com.zaxxer.hikari.HikariDataSource create(Long id) {
        DatasourceRuntimeDefinition source = definition(id);
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setPoolName("workflow-runtime-" + id);
        config.setDriverClassName(source.driverClass());
        config.setJdbcUrl(source.jdbcUrl());
        config.setUsername(source.username());
        config.setPassword(cipher.decrypt(source.encryptedPassword()));
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(5000);
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    private ProjectResourceEntity requireDatasource(Long id) {
        ProjectResourceEntity entity = mapper.selectById(id);
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled()))
            throw new IllegalArgumentException("数据源不存在或已禁用: " + id);
        if (!ProjectResourceType.DATASOURCE.name().equals(entity.getResourceType()))
            throw new IllegalArgumentException("资源不是 JDBC 数据源: " + id);
        return entity;
    }

    @PreDestroy
    public void close() {
        pools.values().forEach(com.zaxxer.hikari.HikariDataSource::close);
    }

    private Map<String, Object> readConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("资源配置 JSON 无效", e);
        }
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

package com.workflowtest.engine.executor;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.workflowtest.engine.persistence.entity.RuntimeDataSourceEntity;
import com.workflowtest.engine.persistence.mapper.RuntimeDataSourceMapper;
import com.workflowtest.engine.security.SecretCipher;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RuntimeDataSourceManager {
    private final RuntimeDataSourceMapper mapper;
    private final SecretCipher cipher;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public RuntimeDataSourceManager(RuntimeDataSourceMapper mapper, SecretCipher cipher) {
        this.mapper = mapper; this.cipher = cipher;
    }

    public DataSource get(String id) {
        return pools.computeIfAbsent(id, this::create);
    }

    public RuntimeDataSourceEntity definition(String id) {
        RuntimeDataSourceEntity entity = mapper.selectById(id);
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled()))
            throw new IllegalArgumentException("数据源不存在或已禁用: " + id);
        return entity;
    }

    private HikariDataSource create(String id) {
        RuntimeDataSourceEntity source = definition(id);
        HikariConfig config = new HikariConfig();
        config.setPoolName("workflow-runtime-" + id.substring(0, Math.min(8, id.length())));
        config.setDriverClassName(source.getDriverClass());
        config.setJdbcUrl(source.getJdbcUrl());
        config.setUsername(source.getUsername());
        config.setPassword(cipher.decrypt(source.getEncryptedPassword()));
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void close() { pools.values().forEach(HikariDataSource::close); }
}

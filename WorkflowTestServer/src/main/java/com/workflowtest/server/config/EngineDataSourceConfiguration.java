package com.workflowtest.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.workflowtest.server.support.CommonConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class EngineDataSourceConfiguration {
    private static final String METADATA_POOL_NAME = "workflowtest-metadata";
    private static final String METADATA_DB_FILE = "workflow-test.db";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String SQLITE_JDBC_PREFIX = "jdbc:sqlite:";

    @Bean(destroyMethod = "close")
    public DataSource dataSource(WorkflowTestProperties properties) throws IOException {
        Path dataDir = properties.getDataDir().toAbsolutePath().normalize();
        Files.createDirectories(dataDir);

        HikariConfig config = new HikariConfig();
        config.setPoolName(METADATA_POOL_NAME);
        config.setDriverClassName(SQLITE_DRIVER);
        config.setJdbcUrl(SQLITE_JDBC_PREFIX + dataDir.resolve(METADATA_DB_FILE));
        config.setMaximumPoolSize(CommonConstant.ONE);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }
}

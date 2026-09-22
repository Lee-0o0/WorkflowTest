package com.workflowtest.engine.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class EngineDataSourceConfiguration {

    @Bean(destroyMethod = "close")
    public DataSource dataSource(WorkflowTestProperties properties) throws IOException {
        Path dataDir = properties.getDataDir().toAbsolutePath().normalize();
        Files.createDirectories(dataDir);

        HikariConfig config = new HikariConfig();
        config.setPoolName("workflowtest-metadata");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dataDir.resolve("workflow-test.db"));
        config.setMaximumPoolSize(1);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }
}

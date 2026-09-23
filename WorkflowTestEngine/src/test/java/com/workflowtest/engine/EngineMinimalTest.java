package com.workflowtest.engine;

import com.workflowtest.engine.api.definition.ProjectTreeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最小 Spring 上下文测试：数据库位于 {@code src/test/resources/workflow-test-data}。
 */
class EngineMinimalTest extends BasicTestApplication {
    private static final Path DATA_DIR = dataDir(EngineMinimalTest.class);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerDataDir(registry, DATA_DIR);
    }

    @Autowired
    private ProjectTreeService projectTree;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(projectTree).isNotNull();
        assertThat(dataSource).isNotNull();
    }

    @Test
    void sqliteAndFlywayAreReady() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getTables(null, null, "ts_project", null).next()).isTrue();
        }
        assertThat(projectTree.listProjects()).isEmpty();
    }
}

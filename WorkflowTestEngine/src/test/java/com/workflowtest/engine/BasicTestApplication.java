package com.workflowtest.engine;

import com.workflowtest.engine.config.WorkflowTestEngineConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(classes = BasicTestApplication.TestApplication.class)
public abstract class BasicTestApplication {
    /** 测试数据库根目录：{@code src/test/resources/workflow-test-data} */
    protected static final Path DATA_ROOT;

    static {
        try {
            DATA_ROOT = resolveTestResourcesRoot();
            Files.createDirectories(DATA_ROOT);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 按测试类划分子目录，避免多个测试类共用同一 SQLite 文件。 */
    protected static Path dataDir(Class<?> testClass) {
        try {
            Path dir = DATA_ROOT.resolve(testClass.getSimpleName());
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected static void registerDataDir(DynamicPropertyRegistry registry, Path dataDir) {
        registry.add("workflowtest.data-dir", dataDir::toString);
    }

    private static Path resolveTestResourcesRoot() {
        Path inModule = Path.of("src/test/resources/workflow-test-data");
        if (Files.isDirectory(Path.of("src/test/resources")) || Files.isDirectory(Path.of("src/test"))) {
            return inModule.toAbsolutePath().normalize();
        }
        return Path.of("WorkflowTestEngine/src/test/resources/workflow-test-data").toAbsolutePath().normalize();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WorkflowTestEngineConfiguration.class)
    static class TestApplication {}
}

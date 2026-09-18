package com.workflowtest.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "workflowtest")
public class WorkflowTestProperties {
    private Path dataDir = Path.of(System.getProperty("user.home"), ".workflowtest", "data");

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }
}

package com.workflowtest.server.config;

import com.workflowtest.engine.WorkflowEngine;
import com.workflowtest.engine.WorkflowEngines;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowEngineConfiguration {

    @Bean(destroyMethod = "close")
    public WorkflowEngine workflowEngine() {
        return WorkflowEngines.createManaged(java.util.List.of());
    }
}

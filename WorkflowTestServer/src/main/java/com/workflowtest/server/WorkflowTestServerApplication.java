package com.workflowtest.server;

import com.workflowtest.server.config.WorkflowTestServerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.workflowtest.server")
@Import(WorkflowTestServerConfiguration.class)
public class WorkflowTestServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowTestServerApplication.class, args);
    }
}

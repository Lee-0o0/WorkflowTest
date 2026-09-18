package com.workflowtest.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@MapperScan("com.workflowtest.server.persistence")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class WorkflowTestServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowTestServerApplication.class, args);
    }
}

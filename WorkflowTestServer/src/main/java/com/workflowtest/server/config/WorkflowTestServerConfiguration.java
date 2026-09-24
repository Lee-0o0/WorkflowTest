package com.workflowtest.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Import({EngineDataSourceConfiguration.class, MyBatisPlusConfiguration.class, AuditMetaObjectHandler.class, WorkflowEngineConfiguration.class})
@ComponentScan(basePackages = {
        "com.workflowtest.server.application",
        "com.workflowtest.server.runtime",
        "com.workflowtest.server.security",
        "com.workflowtest.server.support"
})
@MapperScan("com.workflowtest.server.persistence.mapper")
@EnableTransactionManagement
@EnableConfigurationProperties(WorkflowTestProperties.class)
public class WorkflowTestServerConfiguration {
}

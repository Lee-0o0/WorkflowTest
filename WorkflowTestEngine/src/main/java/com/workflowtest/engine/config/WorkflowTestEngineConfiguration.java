package com.workflowtest.engine.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@Import(ExecutionListenerFactoryConfiguration.class)
@ComponentScan("com.workflowtest.engine")
@MapperScan("com.workflowtest.engine.persistence.mapper")
@EnableTransactionManagement
@EnableConfigurationProperties(WorkflowTestProperties.class)
public class WorkflowTestEngineConfiguration {
}

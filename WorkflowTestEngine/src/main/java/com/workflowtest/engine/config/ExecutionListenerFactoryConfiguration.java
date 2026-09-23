package com.workflowtest.engine.config;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExecutionListenerFactoryConfiguration {
    @Bean
    static BeanDefinitionRegistryPostProcessor executionListenerFactoryBeanRegistrar() {
        return new ExecutionListenerFactoryBeanRegistrar();
    }
}

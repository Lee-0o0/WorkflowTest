package com.workflowtest.engine.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在外部 Spring 应用中启用 WorkflowTest 引擎。
 * <p>
 * 引擎自身只扫描 {@code com.workflowtest.engine}；业务监听器可通过以下方式注册：
 * <ul>
 *   <li>{@code META-INF/spring.factories}，key 为 {@link com.workflowtest.engine.api.execution.listener.ExecutionListener}</li>
 *   <li>外部应用包下的 {@code @Component}</li>
 *   <li>任意配置类中的 {@code @Bean ExecutionListener}</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(WorkflowTestEngineConfiguration.class)
public @interface EnableWorkflowTestEngine {
}

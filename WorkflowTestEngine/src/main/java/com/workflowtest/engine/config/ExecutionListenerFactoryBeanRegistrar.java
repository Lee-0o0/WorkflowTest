package com.workflowtest.engine.config;

import com.workflowtest.engine.api.execution.listener.ExecutionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.lang.reflect.Modifier;

/**
 * 读取类路径 {@code META-INF/spring.factories} 中
 * {@link ExecutionListener} 的实现类并注册为 Spring Bean。
 */
@Slf4j
class ExecutionListenerFactoryBeanRegistrar implements BeanDefinitionRegistryPostProcessor, Ordered {
    static final String BEAN_NAME_PREFIX = "executionListener#";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassLoader classLoader = resolveClassLoader();
        for (String className : SpringFactoriesLoader.loadFactoryNames(ExecutionListener.class, classLoader)) {
            registerListener(registry, className, classLoader);
        }
    }

    private void registerListener(BeanDefinitionRegistry registry, String className, ClassLoader classLoader) {
        String beanName = BEAN_NAME_PREFIX + className;
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        try {
            Class<?> listenerClass = Class.forName(className, false, classLoader);
            if (!ExecutionListener.class.isAssignableFrom(listenerClass)) {
                log.warn("跳过 ExecutionListener 工厂条目 {}：未实现 ExecutionListener", className);
                return;
            }
            if (listenerClass.isInterface() || Modifier.isAbstract(listenerClass.getModifiers())) {
                log.warn("跳过 ExecutionListener 工厂条目 {}：不能实例化抽象类或接口", className);
                return;
            }
            RootBeanDefinition definition = new RootBeanDefinition(listenerClass);
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            definition.setRole(BeanDefinition.ROLE_APPLICATION);
            registry.registerBeanDefinition(beanName, definition);
            log.debug("已从 spring.factories 注册 ExecutionListener: {}", className);
        } catch (ClassNotFoundException e) {
            log.warn("跳过 ExecutionListener 工厂条目 {}：类不存在", className, e);
        }
    }

    private ClassLoader resolveClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : getClass().getClassLoader();
    }

    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

package com.workflowtest.engine.runtime;

import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.executor.StepExecutor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class StepExecutorRegistry {
    private final Map<StepType, StepExecutor> executors = new EnumMap<>(StepType.class);

    public StepExecutorRegistry(List<StepExecutor> executorList) {
        executorList.forEach(executor -> executors.put(executor.supports(), executor));
    }

    public StepExecutor get(StepType type) {
        StepExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("不支持的步骤类型: " + type);
        }
        return executor;
    }
}

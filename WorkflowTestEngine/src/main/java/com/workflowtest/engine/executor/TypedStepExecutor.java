package com.workflowtest.engine.executor;

import com.workflowtest.engine.executor.config.StepConfig;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;

public abstract class TypedStepExecutor<C extends StepConfig> implements StepExecutor {
    protected abstract Class<C> configType();

    protected abstract StepResult doExecute(RuntimeStep step, C config, ExecutionContext context,
                                            RuntimeVariableScope variableScope) throws Exception;

    @Override
    public final StepResult execute(RuntimeStep step, StepConfig config, ExecutionContext context,
                                    RuntimeVariableScope variableScope) throws Exception {
        if (!configType().isInstance(config)) {
            throw new IllegalArgumentException("步骤配置类型不匹配: " + config.getClass().getSimpleName());
        }
        return doExecute(step, configType().cast(config), context, variableScope);
    }
}

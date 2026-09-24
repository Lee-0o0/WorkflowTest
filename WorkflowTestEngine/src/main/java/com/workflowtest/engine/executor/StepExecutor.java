package com.workflowtest.engine.executor;

import com.workflowtest.engine.executor.config.StepConfig;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;

public interface StepExecutor {
    StepType supports();
    StepResult execute(RuntimeStep step, StepConfig config, ExecutionContext context, RuntimeVariableScope variableScope)
            throws Exception;
}

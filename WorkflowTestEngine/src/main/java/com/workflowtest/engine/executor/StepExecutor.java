package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;

public interface StepExecutor {
    StepType supports();
    StepResult execute(RuntimeStep step, JsonNode resolvedConfig, ExecutionContext context) throws Exception;
}

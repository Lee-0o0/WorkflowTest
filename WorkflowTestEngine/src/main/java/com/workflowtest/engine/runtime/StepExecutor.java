package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflowtest.engine.api.DefinitionModels.StepType;

public interface StepExecutor {
    StepType supports();
    StepResult execute(RuntimeStep step, JsonNode resolvedConfig, ExecutionContext context) throws Exception;
}

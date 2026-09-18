package com.workflowtest.engine.runtime;

import com.workflowtest.engine.api.DefinitionModels.FailureStrategy;
import com.workflowtest.engine.api.DefinitionModels.StepType;

public record RuntimeStep(String id, String code, String name, StepType type, int order,
                          String configJson, String extractionJson, String assertionJson,
                          FailureStrategy failureStrategy, String retryJson) {
}

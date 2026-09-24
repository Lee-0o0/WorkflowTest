package com.workflowtest.engine.runtime;

import com.workflowtest.engine.model.StepType;

public record RuntimeStep(Long id, String code, String name, StepType type, int order,
                          String configJson, String extractionJson, String assertionJson) {
}

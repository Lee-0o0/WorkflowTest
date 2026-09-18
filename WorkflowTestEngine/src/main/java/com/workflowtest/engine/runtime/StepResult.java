package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public record StepResult(JsonNode output, JsonNode request, JsonNode response, long elapsedMs) {
}

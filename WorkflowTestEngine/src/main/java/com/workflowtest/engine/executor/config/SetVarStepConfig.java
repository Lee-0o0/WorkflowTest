package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.support.EngineMessages;

import java.util.LinkedHashMap;
import java.util.Map;

public record SetVarStepConfig(Map<String, Object> variables) implements StepConfig {

    public SetVarStepConfig {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static SetVarStepConfig from(JsonNode node, ObjectMapper mapper) {
        JsonNode variablesNode = node.path("variables");
        if (!variablesNode.isObject() || variablesNode.isEmpty()) {
            throw new IllegalArgumentException(EngineMessages.SET_VAR_VARIABLES_REQUIRED);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variablesNode.fields().forEachRemaining(entry ->
                variables.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class)));
        return new SetVarStepConfig(variables);
    }
}

package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.support.EngineMessages;

import java.util.ArrayList;
import java.util.List;

public record DeleteVarStepConfig(List<String> variables) implements StepConfig {

    public DeleteVarStepConfig {
        variables = variables == null ? List.of() : List.copyOf(variables);
    }

    public static DeleteVarStepConfig from(JsonNode node, ObjectMapper mapper) {
        JsonNode variablesNode = node.path("variables");
        if (!variablesNode.isArray() || variablesNode.isEmpty()) {
            throw new IllegalArgumentException(EngineMessages.DELETE_VAR_VARIABLES_REQUIRED);
        }
        List<String> variables = new ArrayList<>();
        variablesNode.forEach(item -> {
            String target = item.asText(null);
            if (target != null && !target.isBlank()) {
                variables.add(target.trim());
            }
        });
        if (variables.isEmpty()) {
            throw new IllegalArgumentException(EngineMessages.DELETE_VAR_VARIABLES_REQUIRED);
        }
        return new DeleteVarStepConfig(variables);
    }
}

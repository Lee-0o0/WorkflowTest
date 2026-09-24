package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.StepType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StepConfigReader {
    private final ObjectMapper objectMapper;

    public StepConfig read(StepType type, JsonNode resolvedNode) {
        if (resolvedNode == null || resolvedNode.isNull()) {
            throw new IllegalArgumentException("步骤配置不能为空");
        }
        return switch (type) {
            case HTTP -> HttpStepConfig.from(resolvedNode, objectMapper);
            case SQL -> SqlStepConfig.from(resolvedNode, objectMapper);
            case DELAY -> DelayStepConfig.from(resolvedNode, objectMapper);
            case SET_VAR -> SetVarStepConfig.from(resolvedNode, objectMapper);
            case DELETE_VAR -> DeleteVarStepConfig.from(resolvedNode, objectMapper);
        };
    }
}

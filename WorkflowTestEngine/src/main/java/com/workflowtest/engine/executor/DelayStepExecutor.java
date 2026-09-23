package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DelayStepExecutor implements StepExecutor {
    private final ObjectMapper objectMapper;
    @Override public StepType supports() { return StepType.DELAY; }

    @Override
    public StepResult execute(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        long millis = config.path("millis").asLong(1000);
        if (millis < 0 || millis > 300_000) throw new IllegalArgumentException("延迟必须在 0 到 300000ms 之间");
        Thread.sleep(millis);
        ObjectNode output = objectMapper.createObjectNode().put("delayedMs", millis);
        return new StepResult(output, objectMapper.nullNode(), objectMapper.nullNode(), millis);
    }
}

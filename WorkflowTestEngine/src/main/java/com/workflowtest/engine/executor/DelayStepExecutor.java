package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.executor.config.DelayStepConfig;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DelayStepExecutor extends TypedStepExecutor<DelayStepConfig> {
    private final ObjectMapper objectMapper;

    @Override
    protected Class<DelayStepConfig> configType() {
        return DelayStepConfig.class;
    }

    @Override
    public StepType supports() {
        return StepType.DELAY;
    }

    @Override
    protected StepResult doExecute(RuntimeStep step, DelayStepConfig config, ExecutionContext context,
                                   RuntimeVariableScope variableScope) throws Exception {
        Thread.sleep(config.millis());
        ObjectNode output = objectMapper.createObjectNode().put("delayedMs", config.millis());
        return new StepResult(output, objectMapper.nullNode(), objectMapper.nullNode(), config.millis());
    }
}

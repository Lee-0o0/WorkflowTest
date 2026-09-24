package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.executor.config.SetVarStepConfig;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SetVarStepExecutor extends TypedStepExecutor<SetVarStepConfig> {
    private final ObjectMapper objectMapper;

    @Override
    protected Class<SetVarStepConfig> configType() {
        return SetVarStepConfig.class;
    }

    @Override
    public StepType supports() {
        return StepType.SET_VAR;
    }

    @Override
    protected StepResult doExecute(RuntimeStep step, SetVarStepConfig config, ExecutionContext context,
                                   RuntimeVariableScope variableScope) throws Exception {
        ObjectNode assigned = objectMapper.createObjectNode();
        config.variables().forEach((target, value) -> {
            context.assignVariable(target, value, variableScope);
            assigned.set(target, objectMapper.valueToTree(value));
        });
        ObjectNode request = objectMapper.valueToTree(config).deepCopy();
        return new StepResult(assigned, request, assigned.deepCopy(), 0);
    }
}

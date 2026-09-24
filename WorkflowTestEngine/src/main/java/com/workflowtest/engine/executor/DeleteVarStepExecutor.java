package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.executor.config.DeleteVarStepConfig;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteVarStepExecutor extends TypedStepExecutor<DeleteVarStepConfig> {
    private final ObjectMapper objectMapper;

    @Override
    protected Class<DeleteVarStepConfig> configType() {
        return DeleteVarStepConfig.class;
    }

    @Override
    public StepType supports() {
        return StepType.DELETE_VAR;
    }

    @Override
    protected StepResult doExecute(RuntimeStep step, DeleteVarStepConfig config, ExecutionContext context,
                                   RuntimeVariableScope variableScope) throws Exception {
        ArrayNode removed = objectMapper.createArrayNode();
        ArrayNode missing = objectMapper.createArrayNode();
        config.variables().forEach(target -> {
            if (context.removeVariable(target, variableScope)) {
                removed.add(target);
            } else {
                missing.add(target);
            }
        });
        ObjectNode output = objectMapper.createObjectNode();
        output.set("removed", removed);
        output.set("missing", missing);
        ObjectNode request = objectMapper.valueToTree(config).deepCopy();
        return new StepResult(output, request, output.deepCopy(), 0);
    }
}

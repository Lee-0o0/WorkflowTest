package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.executor.config.SetVarStepConfig;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetVarStepExecutorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SetVarStepExecutor executor;
    private ExecutionContext workflowContext;
    private ExecutionContext groupContext;

    @BeforeEach
    void setUp() {
        executor = new SetVarStepExecutor(objectMapper);
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        workflowContext = new ExecutionContext(environment, Map.of(), Map.of(), new LinkedHashMap<>(), objectMapper);
        groupContext = new ExecutionContext(environment, Map.of(), new LinkedHashMap<>(), Map.of(), objectMapper);
    }

    @Test
    void supports_returnsSetVar() {
        assertThat(executor.supports()).isEqualTo(StepType.SET_VAR);
    }

    @Test
    void execute_workflowStep_assignsWorkflowVariablesOnly() throws Exception {
        RuntimeStep step = runtimeStep();
        SetVarStepConfig config = SetVarStepConfig.from(objectMapper.readTree("""
                {"variables":{"orderId":1001,"status":"WAIT_PAY"}}
                """), objectMapper);

        StepResult result = executor.execute(step, config, workflowContext, RuntimeVariableScope.WORKFLOW);

        assertThat(workflowContext.resolve("orderId")).isEqualTo(1001);
        assertThat(result.output().path("orderId").asInt()).isEqualTo(1001);
    }

    @Test
    void execute_workflowStep_rejectsGroupTarget() throws Exception {
        SetVarStepConfig config = SetVarStepConfig.from(objectMapper.readTree("""
                {"variables":{"group.channel":"web"}}
                """), objectMapper);

        assertThatThrownBy(() -> executor.execute(runtimeStep(), config, workflowContext, RuntimeVariableScope.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.RUNTIME_VAR_WORKFLOW_TARGET_ONLY, "group.channel"));
    }

    @Test
    void execute_groupHook_assignsGroupVariables() throws Exception {
        SetVarStepConfig config = SetVarStepConfig.from(objectMapper.readTree("""
                {"variables":{"group.channel":"web","token":"abc"}}
                """), objectMapper);

        executor.execute(runtimeStep(), config, groupContext, RuntimeVariableScope.GROUP);

        assertThat(groupContext.resolve("group.channel")).isEqualTo("web");
        assertThat(groupContext.resolve("group.token")).isEqualTo("abc");
    }

    @Test
    void execute_rejectsMissingVariables() {
        assertThatThrownBy(() -> SetVarStepConfig.from(objectMapper.createObjectNode(), objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EngineMessages.SET_VAR_VARIABLES_REQUIRED);
    }

    private static RuntimeStep runtimeStep() {
        return new RuntimeStep(1L, "set", "set", StepType.SET_VAR, 1, "{}", "[]", "[]");
    }
}

package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.executor.config.DeleteVarStepConfig;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteVarStepExecutorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeleteVarStepExecutor executor;
    private ExecutionContext workflowContext;
    private ExecutionContext groupContext;

    @BeforeEach
    void setUp() {
        executor = new DeleteVarStepExecutor(objectMapper);
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        workflowContext = new ExecutionContext(environment, Map.of(), Map.of(), new LinkedHashMap<>(), objectMapper);
        groupContext = new ExecutionContext(environment, Map.of(), new LinkedHashMap<>(Map.of("token", "abc")), Map.of(), objectMapper);
        workflowContext.assignVariable("orderId", 1001, RuntimeVariableScope.WORKFLOW);
    }

    @Test
    void execute_workflowStep_removesWorkflowVariables() throws Exception {
        DeleteVarStepConfig config = DeleteVarStepConfig.from(objectMapper.readTree("""
                {"variables":["orderId","missing"]}
                """), objectMapper);

        var result = executor.execute(runtimeStep(), config, workflowContext, RuntimeVariableScope.WORKFLOW);

        assertThat(workflowContext.resolve("orderId")).isNull();
        assertThat(textValues(result.output().path("removed"))).containsExactly("orderId");
        assertThat(textValues(result.output().path("missing"))).containsExactly("missing");
    }

    @Test
    void execute_groupHook_removesGroupVariables() throws Exception {
        DeleteVarStepConfig config = DeleteVarStepConfig.from(objectMapper.readTree("""
                {"variables":["group.token"]}
                """), objectMapper);

        executor.execute(runtimeStep(), config, groupContext, RuntimeVariableScope.GROUP);

        assertThat(groupContext.resolve("group.token")).isNull();
    }

    @Test
    void execute_workflowStep_rejectsGroupTarget() throws Exception {
        DeleteVarStepConfig config = DeleteVarStepConfig.from(objectMapper.readTree("""
                {"variables":["group.token"]}
                """), objectMapper);

        assertThatThrownBy(() -> executor.execute(runtimeStep(), config, workflowContext, RuntimeVariableScope.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.RUNTIME_VAR_WORKFLOW_TARGET_ONLY, "group.token"));
    }

    private static RuntimeStep runtimeStep() {
        return new RuntimeStep(1L, "del", "del", StepType.DELETE_VAR, 1, "{}", "[]", "[]");
    }

    private static List<String> textValues(JsonNode node) {
        ArrayList<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }
}

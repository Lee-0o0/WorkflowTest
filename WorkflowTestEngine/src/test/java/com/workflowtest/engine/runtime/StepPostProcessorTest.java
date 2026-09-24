package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepPostProcessorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StepPostProcessor processor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        processor = new StepPostProcessor(objectMapper);
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        context = new ExecutionContext(environment, Map.of(), Map.of(), Map.of(), objectMapper);
    }

    @Test
    void extract_rejectsGroupTargetInWorkflowStep() throws Exception {
        var definitions = objectMapper.readTree("""
                [{"target":"group.channel","source":"OUTPUT","expression":"$.value"}]
                """);
        var output = objectMapper.readTree("{\"value\":\"web\"}");
        var result = new StepResult(output, output, output, 0);

        assertThatThrownBy(() -> processor.extract(definitions, result, context, RuntimeVariableScope.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.RUNTIME_VAR_WORKFLOW_TARGET_ONLY, "group.channel"));
    }

    @Test
    void extract_bareTarget_writesWorkflowVariable() throws Exception {
        var definitions = objectMapper.readTree("""
                [{"target":"orderId","source":"OUTPUT","expression":"$.value"}]
                """);
        var output = objectMapper.readTree("{\"value\":42}");
        var result = new StepResult(output, output, output, 0);

        processor.extract(definitions, result, context, RuntimeVariableScope.WORKFLOW);

        assertThat(context.resolve("orderId")).isEqualTo(42);
        assertThat(context.resolve("workflow.orderId")).isEqualTo(42);
    }

    @Test
    void extract_rejectsGlobalTarget() throws Exception {
        var definitions = objectMapper.readTree("""
                [{"target":"global.baseUrl","source":"OUTPUT"}]
                """);
        var result = new StepResult(objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), 0);

        assertThatThrownBy(() -> processor.extract(definitions, result, context, RuntimeVariableScope.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.ENV_VARIABLE_INVALID, "global.baseUrl"));
    }
}

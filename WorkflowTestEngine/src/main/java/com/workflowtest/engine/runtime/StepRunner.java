package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.listener.ExecutionEventAttributes;
import com.workflowtest.engine.listener.ExecutionEventContext;
import com.workflowtest.engine.listener.ExecutionEventType;
import com.workflowtest.engine.application.execution.CancellationException;
import com.workflowtest.engine.application.execution.ExecutionListenerPublisher;
import com.workflowtest.engine.executor.config.StepConfig;
import com.workflowtest.engine.executor.config.StepConfigReader;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class StepRunner {
    private final ObjectMapper objectMapper;
    private final VariableTemplateResolver templateResolver;
    private final StepExecutorRegistry executorRegistry;
    private final StepPostProcessor postProcessor;
    private final ExecutionListenerPublisher listenerPublisher;
    private final StepConfigReader stepConfigReader;

    public record StepRunOutcome(StepResult result, JsonNode extracted, JsonNode assertionResults) {}

    public StepRunOutcome execute(RuntimeStep step, ExecutionContext context, RuntimeVariableScope variableScope)
            throws Exception {
        JsonNode resolvedConfig = templateResolver.resolve(objectMapper.readTree(step.configJson()), context);
        StepConfig config = stepConfigReader.read(step.type(), resolvedConfig);
        JsonNode extractions = templateResolver.resolve(readArray(step.extractionJson()), context);
        JsonNode assertions = templateResolver.resolve(readArray(step.assertionJson()), context);
        StepResult result = executorRegistry.get(step.type()).execute(step, config, context, variableScope);
        JsonNode extracted = postProcessor.extract(extractions, result, context, variableScope);
        JsonNode assertionResults = postProcessor.assertAll(assertions, result);
        context.putStepResult(step.code(), result);
        return new StepRunOutcome(result, extracted, assertionResults);
    }

    public void run(RuntimeStep step, ExecutionContext context, RuntimeVariableScope variableScope,
                    String parentEventId, String phase, AtomicBoolean cancelled) throws Throwable {
        ExecutionEventContext eventContext = stepEventContext(context, step, phase);
        listenerPublisher.notify(ExecutionEventType.STEP_STARTED, parentEventId, step.code(), step.name(), eventContext);
        try {
            checkCancelled(cancelled);
            execute(step, context, variableScope);
            listenerPublisher.notify(ExecutionEventType.STEP_PASSED, parentEventId, step.code(), step.name(), eventContext);
        } catch (Throwable e) {
            listenerPublisher.notify(ExecutionEventType.STEP_FAILED, parentEventId, step.code(), message(e), eventContext);
            throw e;
        }
    }

    private ExecutionEventContext stepEventContext(ExecutionContext context, RuntimeStep step, String phase) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(ExecutionEventAttributes.STEP_CODE, step.code());
        attributes.put(ExecutionEventAttributes.STEP_NAME, step.name());
        attributes.put(ExecutionEventAttributes.PHASE, phase);
        return ExecutionEventContext.of(context.environment(), context.visibleVariables(), attributes);
    }

    private void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new CancellationException();
        }
    }

    private JsonNode readArray(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createArrayNode() : objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(EngineMessages.JSON_ARRAY_INVALID, e);
        }
    }

    private String message(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}

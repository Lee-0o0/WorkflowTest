package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.execution.ExecutionModels.Status;
import com.workflowtest.engine.api.execution.listener.ExecutionEventAttributes;
import com.workflowtest.engine.api.execution.listener.ExecutionEventContext;
import com.workflowtest.engine.api.execution.listener.ExecutionEventType;
import com.workflowtest.engine.application.execution.CancellationException;
import com.workflowtest.engine.application.execution.ExecutionListenerPublisher;
import com.workflowtest.engine.persistence.entity.StepExecutionEntity;
import com.workflowtest.engine.persistence.mapper.HookExecutionMapper;
import com.workflowtest.engine.persistence.mapper.StepExecutionMapper;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 最小步骤执行单元：模板解析 → 执行器调用 → 变量提取 → 断言校验。
 */
@Component
@RequiredArgsConstructor
public class StepRunner {
    private static final String PHASE_WORKFLOW = "WORKFLOW";

    private final ObjectMapper objectMapper;
    private final VariableTemplateResolver templateResolver;
    private final StepExecutorRegistry executorRegistry;
    private final StepPostProcessor postProcessor;
    private final StepExecutionMapper stepExecutionMapper;
    private final HookExecutionMapper hookExecutionMapper;
    private final ExecutionListenerPublisher listenerPublisher;

    public record StepRunOutcome(StepResult result, JsonNode extracted, JsonNode assertionResults) {}

    /** 执行单个步骤的核心逻辑，不写执行记录。 */
    public StepRunOutcome execute(RuntimeStep step, ExecutionContext context, boolean groupScope) throws Exception {
        JsonNode config = templateResolver.resolve(objectMapper.readTree(step.configJson()), context);
        JsonNode extractions = templateResolver.resolve(readArray(step.extractionJson()), context);
        JsonNode assertions = templateResolver.resolve(readArray(step.assertionJson()), context);
        StepResult result = executorRegistry.get(step.type()).execute(step, config, context);
        JsonNode extracted = postProcessor.extract(extractions, result, context, groupScope);
        JsonNode assertionResults = postProcessor.assertAll(assertions, result);
        context.putStepResult(step.code(), result);
        return new StepRunOutcome(result, extracted, assertionResults);
    }

    /** 执行单个步骤并持久化执行记录、发送事件。 */
    public void run(RuntimeStep step, ExecutionContext context, boolean groupScope,
                    Long workflowExecutionId, Long hookExecutionId,
                    AtomicBoolean cancelled) throws Throwable {
        String parentEventId = String.valueOf(workflowExecutionId != null ? workflowExecutionId : hookExecutionId);
        ExecutionEventContext eventContext = stepEventContext(context, step, workflowExecutionId, hookExecutionId);
        listenerPublisher.notify(ExecutionEventType.STEP_STARTED, parentEventId, step.code(), step.name(), eventContext);
        StepExecutionEntity execution = beginStepExecution(step, workflowExecutionId, hookExecutionId);
        long start = System.nanoTime();
        try {
            checkCancelled(cancelled);
            StepRunOutcome outcome = execute(step, context, groupScope);
            markStepPassed(execution, outcome);
            listenerPublisher.notify(ExecutionEventType.STEP_PASSED, parentEventId, step.code(), step.name(), eventContext);
        } catch (Throwable e) {
            markStepFailed(execution, e);
            listenerPublisher.notify(ExecutionEventType.STEP_FAILED, parentEventId, step.code(), message(e), eventContext);
            throw e;
        } finally {
            finishStepExecution(execution, start);
        }
    }

    private StepExecutionEntity beginStepExecution(RuntimeStep step, Long workflowExecutionId, Long hookExecutionId) {
        StepExecutionEntity execution = new StepExecutionEntity();
        execution.setExecutionId(workflowExecutionId);
        execution.setHookExecutionId(hookExecutionId);
        execution.setStepId(step.id());
        execution.setStepCode(step.code());
        execution.setPhase(resolvePhase(hookExecutionId));
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now());
        stepExecutionMapper.insert(execution);
        return execution;
    }

    private void markStepPassed(StepExecutionEntity execution, StepRunOutcome outcome) {
        execution.setStatus(Status.PASSED.name());
        execution.setRequestJson(json(outcome.result().request()));
        execution.setResponseJson(json(outcome.result().response()));
        execution.setOutputJson(json(outcome.result().output()));
        execution.setExtractedJson(json(outcome.extracted()));
        execution.setAssertionJson(json(outcome.assertionResults()));
    }

    private void markStepFailed(StepExecutionEntity execution, Throwable error) {
        execution.setStatus(Status.FAILED.name());
        execution.setErrorMessage(message(error));
    }

    private void finishStepExecution(StepExecutionEntity execution, long startNanos) {
        execution.setFinishedAt(LocalDateTime.now());
        execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        stepExecutionMapper.updateById(execution);
    }

    private ExecutionEventContext stepEventContext(ExecutionContext context, RuntimeStep step,
                                                   Long workflowExecutionId, Long hookExecutionId) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(ExecutionEventAttributes.STEP_CODE, step.code());
        attributes.put(ExecutionEventAttributes.STEP_NAME, step.name());
        if (workflowExecutionId != null) {
            attributes.put(ExecutionEventAttributes.WORKFLOW_EXECUTION_ID, workflowExecutionId);
        }
        if (hookExecutionId != null) {
            attributes.put(ExecutionEventAttributes.HOOK_EXECUTION_ID, hookExecutionId);
        }
        return ExecutionEventContext.of(context.environment(), context.visibleVariables(), attributes);
    }

    private String resolvePhase(Long hookExecutionId) {
        if (hookExecutionId == null) {
            return PHASE_WORKFLOW;
        }
        return hookExecutionMapper.selectById(hookExecutionId).getHookType();
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return CommonConstant.JSON_EMPTY_OBJECT;
        }
    }

    private String message(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}

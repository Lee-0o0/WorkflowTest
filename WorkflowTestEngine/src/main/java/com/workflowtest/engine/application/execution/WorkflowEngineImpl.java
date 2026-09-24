package com.workflowtest.engine.application.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.WorkflowEngine;
import com.workflowtest.engine.WorkflowEngine.ExecutionHandle;
import com.workflowtest.engine.WorkflowEngine.ExecutionResult;
import com.workflowtest.engine.WorkflowEngine.Status;
import com.workflowtest.engine.listener.ExecutionEventAttributes;
import com.workflowtest.engine.listener.ExecutionEventContext;
import com.workflowtest.engine.listener.ExecutionEventType;
import com.workflowtest.engine.executor.support.RuntimeFileResourceManager;
import com.workflowtest.engine.executor.support.RuntimeResourceManager;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.model.plan.GroupExecutionPlan;
import com.workflowtest.engine.model.plan.HookPlan;
import com.workflowtest.engine.model.plan.PlanEntityRef;
import com.workflowtest.engine.model.plan.ProjectExecutionPlan;
import com.workflowtest.engine.model.plan.RuntimeProjectResources;
import com.workflowtest.engine.model.plan.WorkflowExecutionPlan;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepRunner;
import com.workflowtest.engine.support.EngineMessages;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkflowEngineImpl implements WorkflowEngine {
    private static final String BEFORE_EACH_GROUP = "BEFORE_EACH_GROUP";
    private static final String BEFORE_GROUP = "BEFORE_GROUP";
    private static final String AFTER_GROUP = "AFTER_GROUP";

    private final StepRunner stepRunner;
    private final ExecutionListenerPublisher listenerPublisher;
    private final RuntimeResourceManager resourceManager;
    private final RuntimeFileResourceManager fileResourceManager;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    public WorkflowEngineImpl(StepRunner stepRunner,
                              ExecutionListenerPublisher listenerPublisher,
                              RuntimeResourceManager resourceManager,
                              ObjectMapper objectMapper,
                              ExecutorService executor) {
        this.stepRunner = stepRunner;
        this.listenerPublisher = listenerPublisher;
        this.resourceManager = resourceManager;
        this.fileResourceManager = resourceManager.files();
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    public ExecutionHandle execute(ProjectExecutionPlan plan) {
        Objects.requireNonNull(plan, EngineMessages.PACKAGE_EMPTY);
        return submit((executionId, cancelled) -> executeProjectPlan(executionId, plan, cancelled));
    }

    @Override
    public ExecutionHandle execute(GroupExecutionPlan plan) {
        Objects.requireNonNull(plan, EngineMessages.PACKAGE_EMPTY);
        return submit((executionId, cancelled) -> executeGroupPlan(executionId, plan, Map.of(), cancelled));
    }

    @Override
    public ExecutionHandle execute(WorkflowExecutionPlan plan) {
        Objects.requireNonNull(plan, EngineMessages.PACKAGE_EMPTY);
        return submit((executionId, cancelled) -> executeWorkflowPlan(executionId, plan, cancelled));
    }

    @Override
    public void cancel(String executionId) {
        AtomicBoolean flag = cancellations.get(executionId);
        if (flag != null) {
            flag.set(true);
        }
    }

    private ExecutionHandle submit(PlanRunner runner) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                resourceManager.clear();
                return runner.run(executionId, cancelled);
            } finally {
                cancellations.remove(executionId);
                resourceManager.clear();
            }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    private ExecutionResult executeProjectPlan(String executionId, ProjectExecutionPlan plan, AtomicBoolean cancelled) {
        long started = System.nanoTime();
        PlanEntityRef project = required(plan.project(), "执行计划缺少 project");
        mergeResources(plan.resources());
        EffectiveEnvironment environment = environment(plan.environment());
        ExecutionEventContext projectContext = scopedContext(environment, Map.of(
                ExecutionEventAttributes.PROJECT_ID, project.idText()));
        listenerPublisher.notify(ExecutionEventType.PROJECT_STARTED, executionId, project.idText(),
                project.name(), projectContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        Map<String, Object> projectRuntimeVariables = new LinkedHashMap<>();
        try {
            for (GroupExecutionPlan groupPlan : safeList(plan.groups())) {
                checkCancelled(cancelled);
                runProjectHookBeforeGroup(executionId, plan, projectRuntimeVariables, cancelled);
                ExecutionResult groupResult = executeGroupPlan(executionId, groupPlan, projectRuntimeVariables, cancelled);
                projectRuntimeVariables.clear();
                if (groupResult.status() != Status.PASSED) {
                    finalStatus = groupResult.status();
                    errors.addAll(groupResult.errors());
                    break;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED;
            errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, executionId, project.idText(),
                    e.getMessage(), projectContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        listenerPublisher.notify(ExecutionEventType.PROJECT_COMPLETED, executionId, project.idText(),
                finalStatus.name(), withStatus(projectContext, finalStatus));
        return new ExecutionResult(executionId, finalStatus, elapsed, Map.of(), List.copyOf(errors));
    }

    private ExecutionResult executeGroupPlan(String executionId, GroupExecutionPlan plan,
                                             Map<String, Object> projectRuntimeVariables,
                                             AtomicBoolean cancelled) {
        mergeResources(plan.resources());
        long started = System.nanoTime();
        PlanEntityRef group = required(plan.group(), "执行计划缺少 group");
        EffectiveEnvironment environment = environment(plan.environment());
        Map<String, Object> groupVariables = new LinkedHashMap<>();
        ExecutionContext groupContext = new ExecutionContext(environment, projectRuntimeVariables, groupVariables,
                Map.of(), objectMapper, fileResourceManager);
        ExecutionEventContext groupEventContext = eventContext(groupContext, Map.of(
                ExecutionEventAttributes.GROUP_ID, group.idText()));
        listenerPublisher.notify(ExecutionEventType.GROUP_STARTED, executionId, group.idText(),
                group.name(), groupEventContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            runHookSteps(plan.beforeGroup(), executionId, BEFORE_GROUP, groupContext,
                    RuntimeVariableScope.GROUP, cancelled);
            for (WorkflowExecutionPlan workflowPlan : safeList(plan.workflows())) {
                checkCancelled(cancelled);
                ExecutionContext workflowContext = new ExecutionContext(workflowPlan.environment(),
                        projectRuntimeVariables, groupVariables, Map.of(), objectMapper, fileResourceManager);
                mergeResources(workflowPlan.resources());
                ExecutionResult workflowResult = executeWorkflowSteps(executionId, workflowPlan, workflowContext, cancelled);
                workflowContext.clearWorkflowVariables();
                if (workflowResult.status() != Status.PASSED) {
                    finalStatus = mergeStatus(finalStatus, workflowResult.status());
                    errors.addAll(workflowResult.errors());
                    if (workflowResult.status() == Status.CANCELLED) {
                        break;
                    }
                }
            }
            runHookSteps(plan.afterGroup(), executionId, AFTER_GROUP, groupContext,
                    RuntimeVariableScope.GROUP, cancelled);
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED;
            errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, executionId, group.idText(),
                    e.getMessage(), groupEventContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            errors.add(message(e));
        } finally {
            groupContext.clearGroupVariables();
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        listenerPublisher.notify(ExecutionEventType.GROUP_COMPLETED, executionId, group.idText(),
                finalStatus.name(), withStatus(groupEventContext, finalStatus));
        return new ExecutionResult(executionId, finalStatus, elapsed, Map.copyOf(groupVariables), List.copyOf(errors));
    }

    private void runProjectHookBeforeGroup(String executionId, ProjectExecutionPlan projectPlan,
                                           Map<String, Object> projectRuntimeVariables,
                                           AtomicBoolean cancelled) throws Throwable {
        HookPlan hook = projectPlan.beforeEachGroup();
        if (hook == null || hook.isEmpty()) {
            return;
        }
        ExecutionContext context = new ExecutionContext(environment(projectPlan.environment()),
                projectRuntimeVariables, Map.of(), Map.of(), objectMapper, fileResourceManager);
        runHookSteps(hook, executionId, BEFORE_EACH_GROUP, context,
                RuntimeVariableScope.PROJECT, cancelled);
    }

    private ExecutionResult executeWorkflowPlan(String executionId, WorkflowExecutionPlan plan, AtomicBoolean cancelled) {
        mergeResources(plan.resources());
        ExecutionContext context = new ExecutionContext(environment(plan.environment()), Map.of(), Map.of(),
                Map.of(), objectMapper, fileResourceManager);
        try {
            return executeWorkflowSteps(executionId, plan, context, cancelled);
        } finally {
            context.clearWorkflowVariables();
        }
    }

    private ExecutionResult executeWorkflowSteps(String parentEventId, WorkflowExecutionPlan plan,
                                                 ExecutionContext context, AtomicBoolean cancelled) {
        long started = System.nanoTime();
        PlanEntityRef workflow = required(plan.workflow(), "执行计划缺少 workflow");
        ExecutionEventContext workflowEventContext = eventContext(context, Map.of(
                ExecutionEventAttributes.WORKFLOW_ID, workflow.idText()));
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_STARTED, parentEventId, workflow.idText(),
                workflow.name(), workflowEventContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            for (RuntimeStep step : safeList(plan.steps())) {
                checkCancelled(cancelled);
                try {
                    stepRunner.run(step, context, RuntimeVariableScope.WORKFLOW, parentEventId, "WORKFLOW", cancelled);
                } catch (Throwable e) {
                    errors.add(step.name() + ": " + message(e));
                    throw e;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED;
            errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, parentEventId, workflow.idText(),
                    e.getMessage(), workflowEventContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            if (errors.isEmpty()) {
                errors.add(message(e));
            }
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_COMPLETED, parentEventId, workflow.idText(),
                finalStatus.name(), withStatus(workflowEventContext, finalStatus));
        return new ExecutionResult(parentEventId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runHookSteps(HookPlan hook, String parentEventId, String hookType,
                              ExecutionContext context, RuntimeVariableScope variableScope,
                              AtomicBoolean cancelled) throws Throwable {
        if (hook == null || hook.isEmpty()) {
            return;
        }
        ExecutionEventContext hookContext = eventContext(context, Map.of(ExecutionEventAttributes.HOOK_TYPE, hookType));
        listenerPublisher.notify(ExecutionEventType.HOOK_STARTED, parentEventId, hookType, hookType, hookContext);
        try {
            for (RuntimeStep step : hook.steps()) {
                checkCancelled(cancelled);
                stepRunner.run(step, context, variableScope, parentEventId, hookType, cancelled);
            }
            listenerPublisher.notify(ExecutionEventType.HOOK_PASSED, parentEventId, hookType, "钩子成功", hookContext);
        } catch (Throwable e) {
            listenerPublisher.notify(ExecutionEventType.HOOK_FAILED, parentEventId, hookType, message(e), hookContext);
            throw e;
        }
    }

    private ExecutionEventContext eventContext(ExecutionContext context, Map<String, Object> attributes) {
        return ExecutionEventContext.of(context.environment(), context.visibleVariables(), attributes);
    }

    private ExecutionEventContext scopedContext(EffectiveEnvironment environment, Map<String, Object> attributes) {
        return ExecutionEventContext.of(environment, Map.of(), attributes);
    }

    private ExecutionEventContext withStatus(ExecutionEventContext context, Status status) {
        Map<String, Object> attributes = new LinkedHashMap<>(context.attributes());
        attributes.put(ExecutionEventAttributes.STATUS, status.name());
        return ExecutionEventContext.of(context.environment(), context.variables(), attributes);
    }

    private Status mergeStatus(Status current, Status next) {
        if (current == Status.CANCELLED || next == Status.CANCELLED) {
            return Status.CANCELLED;
        }
        if (current == Status.FAILED || next == Status.FAILED) {
            return Status.FAILED;
        }
        return Status.PASSED;
    }

    private void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new CancellationException();
        }
    }

    private void mergeResources(RuntimeProjectResources resources) {
        resourceManager.merge(resources);
    }

    private EffectiveEnvironment environment(EffectiveEnvironment environment) {
        return environment == null ? emptyEnvironment() : environment;
    }

    private EffectiveEnvironment emptyEnvironment() {
        return new EffectiveEnvironment(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String message(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @FunctionalInterface
    private interface PlanRunner {
        ExecutionResult run(String executionId, AtomicBoolean cancelled);
    }
}

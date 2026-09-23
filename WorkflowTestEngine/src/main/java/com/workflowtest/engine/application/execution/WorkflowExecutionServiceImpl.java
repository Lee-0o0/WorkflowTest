package com.workflowtest.engine.application.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.execution.ExecutionControlService;
import com.workflowtest.engine.api.execution.listener.ExecutionEventAttributes;
import com.workflowtest.engine.api.execution.listener.ExecutionEventContext;
import com.workflowtest.engine.api.execution.listener.ExecutionEventType;
import com.workflowtest.engine.api.execution.GroupExecutionService;
import com.workflowtest.engine.api.execution.PackageExecutionService;
import com.workflowtest.engine.api.execution.ProjectExecutionService;
import com.workflowtest.engine.api.execution.WorkflowRunService;
import com.workflowtest.engine.persistence.entity.*;
import com.workflowtest.engine.persistence.mapper.*;
import com.workflowtest.engine.runtime.*;
import com.workflowtest.engine.runtime.EnvironmentResolver.Source;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements ProjectExecutionService, GroupExecutionService,
        WorkflowRunService, PackageExecutionService, ExecutionControlService {
    private static final class ExecutionPackage {
        static final String DEFINITION = "definition";
        static final String ENVIRONMENT = "environment";
        static final String STEPS = "steps";
        static final String WORKFLOW_ID = "workflowId";
        static final String CHECKSUM = "checksum";
        static final String CHECKSUM_PREFIX = "sha256:";
        static final String REMOTE_WORKFLOW_ID = "remote-workflow";
        static final String PROJECT = "project";
        static final String GROUP = "group";
        static final String WORKFLOW = "workflow";
    }

    private static final class StepField {
        static final String CONFIG_JSON = "configJson";
        static final String EXTRACTION_JSON = "extractionJson";
        static final String ASSERTION_JSON = "assertionJson";
        static final String EXTRACTION = "extraction";
        static final String ASSERTIONS = "assertions";
        static final String ENABLED = "enabled";
        static final String CODE = "code";
        static final String NAME = "name";
        static final String TYPE = "type";
        static final String SORT_ORDER = "sortOrder";
        static final String DEFAULT_TYPE = "HTTP";
    }

    private final WorkflowMapper workflowMapper;
    private final WorkflowGroupMapper groupMapper;
    private final ProjectMapper projectMapper;
    private final StepMapper stepMapper;
    private final HookMapper hookMapper;
    private final HookStepMapper hookStepMapper;
    private final GroupExecutionMapper groupExecutionMapper;
    private final WorkflowExecutionMapper workflowExecutionMapper;
    private final HookExecutionMapper hookExecutionMapper;
    private final EnvironmentResolver environmentResolver;
    private final StepRunner stepRunner;
    private final ExecutionListenerPublisher listenerPublisher;
    private final ObjectMapper objectMapper;
    @Qualifier("workflowExecutor")
    private final Executor executor;
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    @Override
    public ExecutionHandle submit(ProjectExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeProject(executionId, command, cancelled);
            }
            finally {
                cancellations.remove(executionId);
            }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(WorkflowExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeDirectWorkflow(command, cancelled);
            }
            finally {
                cancellations.remove(executionId);
            }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(GroupExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeGroup(command, cancelled);
            }
            finally {
                cancellations.remove(executionId);
            }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(PackageExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executePackage(executionId, command, cancelled);
            }
            finally {
                cancellations.remove(executionId);
            }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public void cancel(String executionId) {
        AtomicBoolean flag = cancellations.get(executionId);
        if (flag != null) {
            flag.set(true);
        }
    }

    private ExecutionResult executePackage(String executionId, PackageExecutionCommand command, AtomicBoolean cancelled) {
        long started = System.nanoTime();
        JsonNode wrapper = required(command.executionPackage(), EngineMessages.PACKAGE_EMPTY);
        JsonNode definition = wrapper.path(ExecutionPackage.DEFINITION);
        if (!definition.isObject()) throw new IllegalArgumentException(EngineMessages.PACKAGE_DEFINITION_INVALID);
        verifyPackageChecksum(wrapper, definition);
        String workflowId = wrapper.path(ExecutionPackage.WORKFLOW_ID).asText(ExecutionPackage.REMOTE_WORKFLOW_ID);
        EffectiveEnvironment environment = packageEnvironment(definition.path(ExecutionPackage.ENVIRONMENT));
        ExecutionContext context = new ExecutionContext(environment, new LinkedHashMap<>(), safeMap(command.inputs()), objectMapper);
        List<String> errors = new ArrayList<>(); Status finalStatus = Status.PASSED;
        ExecutionEventContext packageContext = eventContext(context, Map.of(ExecutionEventAttributes.WORKFLOW_ID, workflowId));
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_STARTED, executionId, workflowId, workflowId, packageContext);
        try {
            for (RuntimeStep step : packageSteps(definition.path(ExecutionPackage.STEPS))) {
                try {
                    runPackageStep(step, context, cancelled, executionId);
                } catch (Throwable e) {
                    errors.add(step.name() + ": " + message(e));
                    throw e;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, executionId, workflowId, e.getMessage(), packageContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED; if (errors.isEmpty()) errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_COMPLETED, executionId, workflowId, finalStatus.name(),
                withStatus(packageContext, finalStatus));
        return new ExecutionResult(executionId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runPackageStep(RuntimeStep step, ExecutionContext context,
                                AtomicBoolean cancelled, String executionId) throws Throwable {
        checkCancelled(cancelled);
        ExecutionEventContext stepContext = eventContext(context, Map.of(
                ExecutionEventAttributes.STEP_CODE, step.code(),
                ExecutionEventAttributes.STEP_NAME, step.name()));
        listenerPublisher.notify(ExecutionEventType.STEP_STARTED, executionId, step.code(), step.name(), stepContext);
        try {
            stepRunner.execute(step, context, false);
            listenerPublisher.notify(ExecutionEventType.STEP_PASSED, executionId, step.code(), step.name(), stepContext);
        } catch (Throwable e) {
            listenerPublisher.notify(ExecutionEventType.STEP_FAILED, executionId, step.code(), message(e), stepContext);
            throw e;
        }
    }

    private List<RuntimeStep> packageSteps(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<RuntimeStep> result = new ArrayList<>(); int fallbackOrder = 0;
        for (JsonNode node : nodes) {
            if (!node.path(StepField.ENABLED).asBoolean(true)) {
                continue;
            }
            String code = node.path(StepField.CODE).asText("step" + fallbackOrder);
            String config = node.has(StepField.CONFIG_JSON) ? node.path(StepField.CONFIG_JSON).asText(CommonConstant.JSON_EMPTY_OBJECT) : json(node.path("config"));
            String extraction = node.has(StepField.EXTRACTION_JSON) ? node.path(StepField.EXTRACTION_JSON).asText(CommonConstant.JSON_EMPTY_ARRAY) : json(node.path(StepField.EXTRACTION));
            String assertion = node.has(StepField.ASSERTION_JSON) ? node.path(StepField.ASSERTION_JSON).asText(CommonConstant.JSON_EMPTY_ARRAY) : json(node.path(StepField.ASSERTIONS));
            result.add(new RuntimeStep(null, code, node.path(StepField.NAME).asText(code),
                    StepType.valueOf(node.path(StepField.TYPE).asText(StepField.DEFAULT_TYPE).toUpperCase(Locale.ROOT)),
                    node.path(StepField.SORT_ORDER).asInt(fallbackOrder++), config, arrayOrEmpty(extraction), arrayOrEmpty(assertion)));
        }
        return result.stream().sorted(Comparator.comparingInt(RuntimeStep::order)).toList();
    }

    private String arrayOrEmpty(String jsonValue) {
        return jsonValue == null || jsonValue.equals(CommonConstant.JSON_NULL) || jsonValue.isEmpty()
                ? CommonConstant.JSON_EMPTY_ARRAY : jsonValue;
    }

    @SuppressWarnings("unchecked")
    private EffectiveEnvironment packageEnvironment(JsonNode node) {
        EffectiveEnvironment globalOnly = environmentResolver.resolve(null, null, null);
        Map<String, Object> global = new LinkedHashMap<>(globalOnly.global());
        Map<String, Object> project = node.path(ExecutionPackage.PROJECT).isObject() ? objectMapper.convertValue(node.path(ExecutionPackage.PROJECT), Map.class) : Map.of();
        Map<String, Object> group = node.path(ExecutionPackage.GROUP).isObject() ? objectMapper.convertValue(node.path(ExecutionPackage.GROUP), Map.class) : Map.of();
        Map<String, Object> workflow = node.path(ExecutionPackage.WORKFLOW).isObject() ? objectMapper.convertValue(node.path(ExecutionPackage.WORKFLOW), Map.class) : Map.of();
        Map<String, Object> effective = new LinkedHashMap<>(); Map<String, String> sources = new LinkedHashMap<>();
        mergeEnvironment(effective, sources, global, Source.GLOBAL); mergeEnvironment(effective, sources, project, Source.PROJECT);
        mergeEnvironment(effective, sources, group, Source.GROUP); mergeEnvironment(effective, sources, workflow, Source.WORKFLOW);
        return new EffectiveEnvironment(Map.copyOf(global), Map.copyOf(project), Map.copyOf(group), Map.copyOf(workflow),
                Map.copyOf(effective), Map.copyOf(sources));
    }

    private void mergeEnvironment(Map<String, Object> target, Map<String, String> sources, Map<String, Object> layer, String source) {
        layer.forEach((key, value) -> { target.put(key, value); sources.put(key, source); });
    }

    private void verifyPackageChecksum(JsonNode wrapper, JsonNode definition) {
        String expected = wrapper.path(ExecutionPackage.CHECKSUM).asText(); if (expected.isBlank()) return;
        try {
            String actual = ExecutionPackage.CHECKSUM_PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance(CommonConstant.SHA_256)
                    .digest(objectMapper.writeValueAsString(definition).getBytes(StandardCharsets.UTF_8)));
            if (!expected.equalsIgnoreCase(actual)) {
                throw new IllegalArgumentException(EngineMessages.PACKAGE_CHECKSUM_MISMATCH);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(EngineMessages.PACKAGE_CHECKSUM_FAILED, e);
        }
    }

    private ExecutionResult executeDirectWorkflow(WorkflowExecutionCommand command, AtomicBoolean cancelled) {
        WorkflowEntity workflow = required(workflowMapper.selectById(command.workflowId()), EngineMessages.WORKFLOW_NOT_FOUND);
        WorkflowGroupEntity group = required(groupMapper.selectById(workflow.getGroupId()), EngineMessages.GROUP_NOT_FOUND);
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), EngineMessages.PROJECT_NOT_FOUND);
        EffectiveEnvironment environment = environmentResolver.resolve(project.getId(), group.getId(), workflow.getId());
        ExecutionContext context = new ExecutionContext(environment, new LinkedHashMap<>(),
                safeMap(command.inputs()), objectMapper);
        return executeWorkflow(null, workflow, context, cancelled);
    }

    private ExecutionResult executeProject(String projectExecutionId, ProjectExecutionCommand command, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        ProjectEntity project = required(projectMapper.selectById(command.projectId()), "项目不存在");
        ExecutionEventContext projectContext = scopedContext(
                environmentResolver.resolve(project.getId(), null, null),
                Map.of(ExecutionEventAttributes.PROJECT_ID, project.getId()));
        listenerPublisher.notify(ExecutionEventType.PROJECT_STARTED, projectExecutionId, String.valueOf(project.getId()),
                project.getName(), projectContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            List<WorkflowGroupEntity> groups = groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                    .eq(WorkflowGroupEntity::getProjectId, project.getId())
                    .eq(WorkflowGroupEntity::getEnabled, true)
                    .orderByAsc(WorkflowGroupEntity::getSortOrder));
            for (WorkflowGroupEntity group : groups) {
                checkCancelled(cancelled);
                ExecutionResult groupResult = executeGroup(
                        new GroupExecutionCommand(group.getId(), safeMap(command.inputs())), cancelled);
                if (groupResult.status() != Status.PASSED) {
                    finalStatus = groupResult.status();
                    errors.addAll(groupResult.errors());
                    break;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, projectExecutionId, String.valueOf(project.getId()),
                    e.getMessage(), projectContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        listenerPublisher.notify(ExecutionEventType.PROJECT_COMPLETED, projectExecutionId, String.valueOf(project.getId()),
                finalStatus.name(), withStatus(projectContext, finalStatus));
        return new ExecutionResult(projectExecutionId, finalStatus, elapsed, Map.of(), List.copyOf(errors));
    }

    private ExecutionResult executeGroup(GroupExecutionCommand command, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        WorkflowGroupEntity group = required(groupMapper.selectById(command.groupId()), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        EffectiveEnvironment groupEnvironment = environmentResolver.resolve(project.getId(), group.getId(), null);
        Map<String, Object> groupVariables = new LinkedHashMap<>(safeMap(command.inputs()));
        ExecutionContext groupContext = new ExecutionContext(groupEnvironment, groupVariables, Map.of(), objectMapper);
        GroupExecutionEntity execution = beginGroupExecution(project.getId(), group.getId(), command.inputs(), groupEnvironment);
        Long groupExecutionId = execution.getId();
        String eventId = String.valueOf(groupExecutionId);
        ExecutionEventContext groupEventContext = eventContext(groupContext, Map.of(
                ExecutionEventAttributes.PROJECT_ID, project.getId(),
                ExecutionEventAttributes.GROUP_ID, group.getId(),
                ExecutionEventAttributes.GROUP_EXECUTION_ID, groupExecutionId));
        listenerPublisher.notify(ExecutionEventType.GROUP_STARTED, eventId, String.valueOf(group.getId()), group.getName(),
                groupEventContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            runHook(group.getId(), HookType.BEFORE_GROUP, groupExecutionId,
                    null, groupContext, true, cancelled);
            List<WorkflowEntity> workflows = workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                    .eq(WorkflowEntity::getGroupId, group.getId()).eq(WorkflowEntity::getEnabled, true)
                    .orderByAsc(WorkflowEntity::getSortOrder));
            for (WorkflowEntity workflow : workflows) {
                checkCancelled(cancelled);
                EffectiveEnvironment workflowEnvironment = environmentResolver.resolve(project.getId(), group.getId(), workflow.getId());
                ExecutionContext workflowContext = new ExecutionContext(workflowEnvironment, groupVariables, Map.of(), objectMapper);
                ExecutionResult result = executeWorkflow(groupExecutionId, workflow, workflowContext, cancelled);
                if (result.status() != Status.PASSED) {
                    finalStatus = mergeStatus(finalStatus, result.status());
                    errors.addAll(result.errors());
                    if (result.status() == Status.CANCELLED) break;
                }
            }
            runHook(group.getId(), HookType.AFTER_GROUP, groupExecutionId,
                    null, groupContext, true, cancelled);
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, eventId, String.valueOf(group.getId()),
                    e.getMessage(), groupEventContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        finishGroupExecution(execution, finalStatus, startNanos, groupVariables, errors);
        listenerPublisher.notify(ExecutionEventType.GROUP_COMPLETED, eventId, String.valueOf(group.getId()), finalStatus.name(),
                withStatus(groupEventContext, finalStatus));
        return new ExecutionResult(eventId, finalStatus, execution.getElapsedMs(), Map.copyOf(groupVariables), List.copyOf(errors));
    }

    private ExecutionResult executeWorkflow(Long groupExecutionId, WorkflowEntity workflow,
                                            ExecutionContext context, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        List<StepEntity> steps = stepMapper.selectList(
                Wrappers.<StepEntity>lambdaQuery()
                    .eq(StepEntity::getWorkflowId, workflow.getId())
                    .eq(StepEntity::getEnabled, true)
                    .orderByAsc(StepEntity::getSortOrder)
        );
        WorkflowExecutionEntity execution = beginWorkflowExecution(groupExecutionId, workflow, context, steps);
        Long workflowExecutionId = execution.getId();
        String eventId = String.valueOf(workflowExecutionId);
        Map<String, Object> workflowAttributes = new LinkedHashMap<>();
        workflowAttributes.put(ExecutionEventAttributes.WORKFLOW_ID, workflow.getId());
        workflowAttributes.put(ExecutionEventAttributes.WORKFLOW_EXECUTION_ID, workflowExecutionId);
        if (groupExecutionId != null) {
            workflowAttributes.put(ExecutionEventAttributes.GROUP_EXECUTION_ID, groupExecutionId);
        }
        ExecutionEventContext workflowEventContext = eventContext(context, workflowAttributes);
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_STARTED, eventId, String.valueOf(workflow.getId()), workflow.getName(),
                workflowEventContext);
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            for (StepEntity entity : steps) {
                checkCancelled(cancelled);
                RuntimeStep step = runtime(entity);
                try {
                    stepRunner.run(step, context, false, workflowExecutionId, null, cancelled);
                } catch (Throwable e) {
                    errors.add(step.name() + ": " + message(e));
                    throw e;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            listenerPublisher.notify(ExecutionEventType.EXECUTION_CANCELLED, eventId, String.valueOf(workflow.getId()),
                    e.getMessage(), workflowEventContext);
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            if (errors.isEmpty()) errors.add(message(e));
        }
        finishWorkflowExecution(execution, finalStatus, startNanos, context, errors);
        listenerPublisher.notify(ExecutionEventType.WORKFLOW_COMPLETED, eventId, String.valueOf(workflow.getId()), finalStatus.name(),
                withStatus(workflowEventContext, finalStatus));
        return new ExecutionResult(eventId, finalStatus, execution.getElapsedMs(), context.visibleVariables(), List.copyOf(errors));
    }

    private void runHook(Long groupId, HookType hookType,
                         Long groupExecutionId, Long workflowExecutionId,
                         ExecutionContext context, boolean groupScope,
                         AtomicBoolean cancelled) throws Throwable {
        HookEntity hook = hookMapper.selectOne(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getGroupId, groupId)
                .eq(HookEntity::getHookType, hookType.name())
                .eq(HookEntity::getEnabled, true));
        if (hook == null) {
            return;
        }
        HookExecutionEntity execution = beginHookExecution(groupExecutionId, workflowExecutionId, hook.getId(), hookType);
        Long hookExecutionId = execution.getId();
        String parentEventId = String.valueOf(workflowExecutionId != null ? workflowExecutionId : groupExecutionId);
        Map<String, Object> hookAttributes = new LinkedHashMap<>();
        hookAttributes.put(ExecutionEventAttributes.HOOK_TYPE, hookType.name());
        hookAttributes.put(ExecutionEventAttributes.HOOK_EXECUTION_ID, hookExecutionId);
        if (groupExecutionId != null) {
            hookAttributes.put(ExecutionEventAttributes.GROUP_EXECUTION_ID, groupExecutionId);
        }
        if (workflowExecutionId != null) {
            hookAttributes.put(ExecutionEventAttributes.WORKFLOW_EXECUTION_ID, workflowExecutionId);
        }
        ExecutionEventContext hookEventContext = eventContext(context, hookAttributes);
        listenerPublisher.notify(ExecutionEventType.HOOK_STARTED, parentEventId, hookType.name(), hookType.name(), hookEventContext);
        long start = System.nanoTime();
        try {
            List<HookStepEntity> steps = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                    .eq(HookStepEntity::getHookId, hook.getId()).eq(HookStepEntity::getEnabled, true)
                    .orderByAsc(HookStepEntity::getSortOrder));
            for (HookStepEntity entity : steps) {
                checkCancelled(cancelled);
                stepRunner.run(runtime(entity), context, groupScope, null, hookExecutionId, cancelled);
            }
            markHookPassed(execution);
            listenerPublisher.notify(ExecutionEventType.HOOK_PASSED, parentEventId, hookType.name(), "钩子成功", hookEventContext);
        } catch (Throwable e) {
            markHookFailed(execution, e);
            listenerPublisher.notify(ExecutionEventType.HOOK_FAILED, parentEventId, hookType.name(), message(e), hookEventContext);
            throw e;
        } finally {
            finishHookExecution(execution, context, start);
        }
    }

    private GroupExecutionEntity beginGroupExecution(Long projectId, Long groupId,
                                                   Map<String, Object> inputs, EffectiveEnvironment environment) {
        GroupExecutionEntity execution = new GroupExecutionEntity();
        execution.setProjectId(projectId);
        execution.setGroupId(groupId);
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now());
        execution.setInputJson(json(inputs));
        execution.setEnvironmentSnapshot(json(environment));
        groupExecutionMapper.insert(execution);
        return execution;
    }

    private void finishGroupExecution(GroupExecutionEntity execution, Status finalStatus, long startNanos,
                                      Map<String, Object> contextVariables, List<String> errors) {
        execution.setStatus(finalStatus.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        execution.setContextSnapshot(json(contextVariables));
        execution.setErrorMessage(String.join("; ", errors));
        groupExecutionMapper.updateById(execution);
    }

    private WorkflowExecutionEntity beginWorkflowExecution(Long groupExecutionId, WorkflowEntity workflow,
                                                           ExecutionContext context, List<StepEntity> steps) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setGroupExecutionId(groupExecutionId);
        execution.setWorkflowId(workflow.getId());
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now());
        execution.setInputJson(CommonConstant.JSON_EMPTY_OBJECT);
        execution.setEnvironmentSnapshot(json(context.environment()));
        execution.setWorkflowSnapshot(json(Map.of("workflow", workflow, "steps", steps)));
        workflowExecutionMapper.insert(execution);
        return execution;
    }

    private void finishWorkflowExecution(WorkflowExecutionEntity execution, Status finalStatus, long startNanos,
                                         ExecutionContext context, List<String> errors) {
        execution.setStatus(finalStatus.name());
        execution.setFinishedAt(LocalDateTime.now());
        execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        execution.setContextSnapshot(json(context.visibleVariables()));
        execution.setErrorMessage(String.join("; ", errors));
        workflowExecutionMapper.updateById(execution);
    }

    private HookExecutionEntity beginHookExecution(Long groupExecutionId, Long workflowExecutionId,
                                                   Long hookId, HookType hookType) {
        HookExecutionEntity execution = new HookExecutionEntity();
        execution.setGroupExecutionId(groupExecutionId);
        execution.setWorkflowExecutionId(workflowExecutionId);
        execution.setHookId(hookId);
        execution.setHookType(hookType.name());
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now());
        hookExecutionMapper.insert(execution);
        return execution;
    }

    private void markHookPassed(HookExecutionEntity execution) {
        execution.setStatus(Status.PASSED.name());
    }

    private void markHookFailed(HookExecutionEntity execution, Throwable error) {
        execution.setStatus(Status.FAILED.name());
        execution.setErrorMessage(message(error));
    }

    private void finishHookExecution(HookExecutionEntity execution, ExecutionContext context, long startNanos) {
        execution.setFinishedAt(LocalDateTime.now());
        execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        execution.setOutputJson(json(context.visibleVariables()));
        hookExecutionMapper.updateById(execution);
    }

    private RuntimeStep runtime(StepEntity e) {
        return new RuntimeStep(e.getId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()),
                e.getSortOrder(), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson());
    }
    private RuntimeStep runtime(HookStepEntity e) {
        return new RuntimeStep(e.getId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()),
                e.getSortOrder(), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson());
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

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return CommonConstant.JSON_EMPTY_OBJECT;
        }
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String message(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}

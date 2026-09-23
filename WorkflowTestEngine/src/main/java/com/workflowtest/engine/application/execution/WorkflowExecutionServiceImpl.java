package com.workflowtest.engine.application.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.execution.ExecutionControlService;
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
            try { return executeProject(executionId, command, cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(WorkflowExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executeDirectWorkflow(command, cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(GroupExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executeGroup(command, cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(PackageExecutionCommand command) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executePackage(executionId, command, cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public void cancel(String executionId) {
        AtomicBoolean flag = cancellations.get(executionId);
        if (flag != null) flag.set(true);
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
        listenerPublisher.notify( ExecutionEventType.WORKFLOW_STARTED, executionId, workflowId, workflowId);
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
            listenerPublisher.notify( ExecutionEventType.EXECUTION_CANCELLED, executionId, workflowId, e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; if (errors.isEmpty()) errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        listenerPublisher.notify( ExecutionEventType.WORKFLOW_COMPLETED, executionId, workflowId, finalStatus.name());
        return new ExecutionResult(executionId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runPackageStep(RuntimeStep step, ExecutionContext context,
                                AtomicBoolean cancelled, String executionId) throws Throwable {
        checkCancelled(cancelled);
        listenerPublisher.notify( ExecutionEventType.STEP_STARTED, executionId, step.code(), step.name());
        try {
            stepRunner.execute(step, context, false);
            listenerPublisher.notify( ExecutionEventType.STEP_PASSED, executionId, step.code(), step.name());
        } catch (Throwable e) {
            listenerPublisher.notify( ExecutionEventType.STEP_FAILED, executionId, step.code(), message(e));
            throw e;
        }
    }

    private List<RuntimeStep> packageSteps(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<RuntimeStep> result = new ArrayList<>(); int fallbackOrder = 0;
        for (JsonNode node : nodes) {
            if (!node.path(StepField.ENABLED).asBoolean(true)) continue;
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
            if (!expected.equalsIgnoreCase(actual)) throw new IllegalArgumentException(EngineMessages.PACKAGE_CHECKSUM_MISMATCH);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException(EngineMessages.PACKAGE_CHECKSUM_FAILED, e); }
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
        listenerPublisher.notify( ExecutionEventType.PROJECT_STARTED, projectExecutionId, String.valueOf(project.getId()), project.getName());
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
            listenerPublisher.notify( ExecutionEventType.EXECUTION_CANCELLED, projectExecutionId, String.valueOf(project.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        listenerPublisher.notify( ExecutionEventType.PROJECT_COMPLETED, projectExecutionId, String.valueOf(project.getId()), finalStatus.name());
        return new ExecutionResult(projectExecutionId, finalStatus, elapsed, Map.of(), List.copyOf(errors));
    }

    private ExecutionResult executeGroup(GroupExecutionCommand command, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        LocalDateTime startedAt = LocalDateTime.now();
        WorkflowGroupEntity group = required(groupMapper.selectById(command.groupId()), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        EffectiveEnvironment groupEnvironment = environmentResolver.resolve(project.getId(), group.getId(), null);
        Map<String, Object> groupVariables = new LinkedHashMap<>(safeMap(command.inputs()));
        ExecutionContext groupContext = new ExecutionContext(groupEnvironment, groupVariables, Map.of(), objectMapper);
        GroupExecutionEntity execution = new GroupExecutionEntity();
        execution.setProjectId(project.getId()); execution.setGroupId(group.getId());
        execution.setStatus(Status.RUNNING.name()); execution.setStartedAt(startedAt);
        execution.setInputJson(json(command.inputs())); execution.setEnvironmentSnapshot(json(groupEnvironment));
        groupExecutionMapper.insert(execution);
        Long groupExecutionId = execution.getId();
        String eventId = String.valueOf(groupExecutionId);
        listenerPublisher.notify( ExecutionEventType.GROUP_STARTED, eventId, String.valueOf(group.getId()), group.getName());
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
            listenerPublisher.notify( ExecutionEventType.EXECUTION_CANCELLED, eventId, String.valueOf(group.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        execution.setStatus(finalStatus.name()); execution.setFinishedAt(LocalDateTime.now()); execution.setElapsedMs(elapsed);
        execution.setContextSnapshot(json(groupVariables)); execution.setErrorMessage(String.join("; ", errors));
        groupExecutionMapper.updateById(execution);
        listenerPublisher.notify( ExecutionEventType.GROUP_COMPLETED, eventId, String.valueOf(group.getId()), finalStatus.name());
        return new ExecutionResult(eventId, finalStatus, elapsed, Map.copyOf(groupVariables), List.copyOf(errors));
    }

    private ExecutionResult executeWorkflow(Long groupExecutionId, WorkflowEntity workflow,
                                            ExecutionContext context, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setGroupExecutionId(groupExecutionId);
        execution.setWorkflowId(workflow.getId());
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now());
        execution.setInputJson("{}");
        execution.setEnvironmentSnapshot(json(context.environment()));
        List<StepEntity> steps = stepMapper.selectList(
                Wrappers.<StepEntity>lambdaQuery()
                    .eq(StepEntity::getWorkflowId, workflow.getId())
                    .eq(StepEntity::getEnabled, true)
                    .orderByAsc(StepEntity::getSortOrder)
        );
        execution.setWorkflowSnapshot(json(Map.of("workflow", workflow, "steps", steps)));
        workflowExecutionMapper.insert(execution);
        Long workflowExecutionId = execution.getId();
        String eventId = String.valueOf(workflowExecutionId);
        listenerPublisher.notify( ExecutionEventType.WORKFLOW_STARTED, eventId, String.valueOf(workflow.getId()), workflow.getName());
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
            listenerPublisher.notify( ExecutionEventType.EXECUTION_CANCELLED, eventId, String.valueOf(workflow.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            if (errors.isEmpty()) errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        execution.setStatus(finalStatus.name()); execution.setFinishedAt(LocalDateTime.now()); execution.setElapsedMs(elapsed);
        execution.setContextSnapshot(json(context.visibleVariables())); execution.setErrorMessage(String.join("; ", errors));
        workflowExecutionMapper.updateById(execution);
        listenerPublisher.notify( ExecutionEventType.WORKFLOW_COMPLETED, eventId, String.valueOf(workflow.getId()), finalStatus.name());
        return new ExecutionResult(eventId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runHook(Long groupId, HookType hookType,
                         Long groupExecutionId, Long workflowExecutionId,
                         ExecutionContext context, boolean groupScope,
                         AtomicBoolean cancelled) throws Throwable {
        HookEntity hook = hookMapper.selectOne(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getGroupId, groupId)
                .eq(HookEntity::getHookType, hookType.name()).eq(HookEntity::getEnabled, true));
        if (hook == null) return;
        HookExecutionEntity execution = new HookExecutionEntity();
        execution.setGroupExecutionId(groupExecutionId);
        execution.setWorkflowExecutionId(workflowExecutionId); execution.setHookId(hook.getId());
        execution.setHookType(hookType.name()); execution.setStatus(Status.RUNNING.name()); execution.setStartedAt(LocalDateTime.now());
        hookExecutionMapper.insert(execution);
        Long hookExecutionId = execution.getId();
        String parentEventId = String.valueOf(workflowExecutionId != null ? workflowExecutionId : groupExecutionId);
        listenerPublisher.notify( ExecutionEventType.HOOK_STARTED, parentEventId, hookType.name(), hookType.name());
        long start = System.nanoTime();
        try {
            List<HookStepEntity> steps = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                    .eq(HookStepEntity::getHookId, hook.getId()).eq(HookStepEntity::getEnabled, true)
                    .orderByAsc(HookStepEntity::getSortOrder));
            for (HookStepEntity entity : steps) {
                checkCancelled(cancelled);
                stepRunner.run(runtime(entity), context, groupScope, null, hookExecutionId, cancelled);
            }
            execution.setStatus(Status.PASSED.name());
            listenerPublisher.notify( ExecutionEventType.HOOK_PASSED, parentEventId, hookType.name(), "钩子成功");
        } catch (Throwable e) {
            execution.setStatus(Status.FAILED.name()); execution.setErrorMessage(message(e));
            listenerPublisher.notify( ExecutionEventType.HOOK_FAILED, parentEventId, hookType.name(), message(e));
            throw e;
        } finally {
            execution.setFinishedAt(LocalDateTime.now());
            execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - start).toMillis());
            execution.setOutputJson(json(context.visibleVariables())); hookExecutionMapper.updateById(execution);
        }
    }

    private RuntimeStep runtime(StepEntity e) {
        return new RuntimeStep(e.getId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()),
                e.getSortOrder(), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson());
    }
    private RuntimeStep runtime(HookStepEntity e) {
        return new RuntimeStep(e.getId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()),
                e.getSortOrder(), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson());
    }

    private Status mergeStatus(Status current, Status next) {
        if (current == Status.CANCELLED || next == Status.CANCELLED) return Status.CANCELLED;
        if (current == Status.FAILED || next == Status.FAILED) return Status.FAILED;
        return Status.PASSED;
    }

    private void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new CancellationException();
        }
    }
    private Map<String, Object> safeMap(Map<String, Object> value) { return value == null ? Map.of() : value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return CommonConstant.JSON_EMPTY_OBJECT; } }
    private <T> T required(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
    private String message(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}

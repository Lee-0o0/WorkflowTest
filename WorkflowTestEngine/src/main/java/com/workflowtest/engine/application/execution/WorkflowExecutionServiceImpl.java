package com.workflowtest.engine.application.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.execution.ExecutionControlService;
import com.workflowtest.engine.api.execution.GroupExecutionService;
import com.workflowtest.engine.api.execution.PackageExecutionService;
import com.workflowtest.engine.api.execution.ProjectExecutionService;
import com.workflowtest.engine.api.execution.WorkflowRunService;
import com.workflowtest.engine.persistence.entity.*;
import com.workflowtest.engine.persistence.mapper.*;
import com.workflowtest.engine.runtime.*;
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
    private final WorkflowMapper workflowMapper;
    private final WorkflowGroupMapper groupMapper;
    private final ProjectMapper projectMapper;
    private final StepMapper stepMapper;
    private final HookMapper hookMapper;
    private final HookStepMapper hookStepMapper;
    private final GroupExecutionMapper groupExecutionMapper;
    private final WorkflowExecutionMapper workflowExecutionMapper;
    private final HookExecutionMapper hookExecutionMapper;
    private final StepExecutionMapper stepExecutionMapper;
    private final EnvironmentResolver environmentResolver;
    private final VariableTemplateResolver templateResolver;
    private final StepExecutorRegistry executorRegistry;
    private final StepPostProcessor postProcessor;
    private final ObjectMapper objectMapper;
    @Qualifier("workflowExecutor")
    private final Executor executor;
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    @Override
    public ExecutionHandle submit(ProjectExecutionCommand command, ExecutionListener listener) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executeProject(executionId, command, safe(listener), cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(WorkflowExecutionCommand command, ExecutionListener listener) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executeDirectWorkflow(command, safe(listener), cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(GroupExecutionCommand command, ExecutionListener listener) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executeGroup(command, safe(listener), cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public ExecutionHandle submit(PackageExecutionCommand command, ExecutionListener listener) {
        String executionId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.put(executionId, cancelled);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try { return executePackage(executionId, command, safe(listener), cancelled); }
            finally { cancellations.remove(executionId); }
        }, executor);
        return new ExecutionHandle(executionId, future);
    }

    @Override
    public void cancel(String executionId) {
        AtomicBoolean flag = cancellations.get(executionId);
        if (flag != null) flag.set(true);
    }

    private ExecutionResult executePackage(String executionId, PackageExecutionCommand command,
                                           ExecutionListener listener, AtomicBoolean cancelled) {
        long started = System.nanoTime();
        JsonNode wrapper = required(command.executionPackage(), "执行包不能为空");
        JsonNode definition = wrapper.path("definition");
        if (!definition.isObject()) throw new IllegalArgumentException("执行包 definition 无效");
        verifyPackageChecksum(wrapper, definition);
        String workflowId = wrapper.path("workflowId").asText("remote-workflow");
        EffectiveEnvironment environment = packageEnvironment(definition.path("environment"));
        ExecutionContext context = new ExecutionContext(environment, new LinkedHashMap<>(), safeMap(command.inputs()), objectMapper);
        List<String> errors = new ArrayList<>(); Status finalStatus = Status.PASSED;
        emit(listener, EventType.WORKFLOW_STARTED, executionId, workflowId, workflowId);
        try {
            for (RuntimeStep step : packageSteps(definition.path("steps"))) {
                try {
                    runPackageStep(step, context, listener, cancelled, executionId);
                } catch (Throwable e) {
                    errors.add(step.name() + ": " + message(e));
                    throw e;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            emit(listener, EventType.EXECUTION_CANCELLED, executionId, workflowId, e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; if (errors.isEmpty()) errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        emit(listener, EventType.WORKFLOW_COMPLETED, executionId, workflowId, finalStatus.name());
        return new ExecutionResult(executionId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runPackageStep(RuntimeStep step, ExecutionContext context, ExecutionListener listener,
                                AtomicBoolean cancelled, String executionId) throws Throwable {
        checkCancelled(cancelled); emit(listener, EventType.STEP_STARTED, executionId, step.code(), step.name());
        try {
            JsonNode config = templateResolver.resolve(objectMapper.readTree(step.configJson()), context);
            JsonNode extractions = templateResolver.resolve(readArray(step.extractionJson()), context);
            JsonNode assertions = templateResolver.resolve(readArray(step.assertionJson()), context);
            StepResult result = executeStep(step, config, context);
            postProcessor.extract(extractions, result, context, false);
            postProcessor.assertAll(assertions, result); context.putStepResult(step.code(), result);
            emit(listener, EventType.STEP_PASSED, executionId, step.code(), step.name());
        } catch (Throwable e) {
            emit(listener, EventType.STEP_FAILED, executionId, step.code(), message(e)); throw e;
        }
    }

    private List<RuntimeStep> packageSteps(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<RuntimeStep> result = new ArrayList<>(); int fallbackOrder = 0;
        for (JsonNode node : nodes) {
            if (!node.path("enabled").asBoolean(true)) continue;
            String code = node.path("code").asText("step" + fallbackOrder);
            String config = node.has("configJson") ? node.path("configJson").asText("{}") : json(node.path("config"));
            String extraction = node.has("extractionJson") ? node.path("extractionJson").asText("[]") : json(node.path("extraction"));
            String assertion = node.has("assertionJson") ? node.path("assertionJson").asText("[]") : json(node.path("assertions"));
            result.add(new RuntimeStep(null, code, node.path("name").asText(code),
                    StepType.valueOf(node.path("type").asText("HTTP").toUpperCase(Locale.ROOT)),
                    node.path("sortOrder").asInt(fallbackOrder++), config, arrayOrEmpty(extraction), arrayOrEmpty(assertion)));
        }
        return result.stream().sorted(Comparator.comparingInt(RuntimeStep::order)).toList();
    }

    private String arrayOrEmpty(String jsonValue) { return jsonValue == null || jsonValue.equals("null") || jsonValue.equals("") ? "[]" : jsonValue; }

    @SuppressWarnings("unchecked")
    private EffectiveEnvironment packageEnvironment(JsonNode node) {
        EffectiveEnvironment globalOnly = environmentResolver.resolve(null, null, null);
        Map<String, Object> global = new LinkedHashMap<>(globalOnly.global());
        Map<String, Object> project = node.path("project").isObject() ? objectMapper.convertValue(node.path("project"), Map.class) : Map.of();
        Map<String, Object> group = node.path("group").isObject() ? objectMapper.convertValue(node.path("group"), Map.class) : Map.of();
        Map<String, Object> workflow = node.path("workflow").isObject() ? objectMapper.convertValue(node.path("workflow"), Map.class) : Map.of();
        Map<String, Object> effective = new LinkedHashMap<>(); Map<String, String> sources = new LinkedHashMap<>();
        mergeEnvironment(effective, sources, global, "GLOBAL"); mergeEnvironment(effective, sources, project, "PROJECT");
        mergeEnvironment(effective, sources, group, "GROUP"); mergeEnvironment(effective, sources, workflow, "WORKFLOW");
        return new EffectiveEnvironment(Map.copyOf(global), Map.copyOf(project), Map.copyOf(group), Map.copyOf(workflow),
                Map.copyOf(effective), Map.copyOf(sources));
    }

    private void mergeEnvironment(Map<String, Object> target, Map<String, String> sources, Map<String, Object> layer, String source) {
        layer.forEach((key, value) -> { target.put(key, value); sources.put(key, source); });
    }

    private void verifyPackageChecksum(JsonNode wrapper, JsonNode definition) {
        String expected = wrapper.path("checksum").asText(); if (expected.isBlank()) return;
        try {
            String actual = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(definition).getBytes(StandardCharsets.UTF_8)));
            if (!expected.equalsIgnoreCase(actual)) throw new IllegalArgumentException("执行包校验和不匹配，拒绝执行");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("执行包校验失败", e); }
    }

    private ExecutionResult executeDirectWorkflow(WorkflowExecutionCommand command,
                                                  ExecutionListener listener, AtomicBoolean cancelled) {
        WorkflowEntity workflow = required(workflowMapper.selectById(command.workflowId()), "工作流不存在");
        WorkflowGroupEntity group = required(groupMapper.selectById(workflow.getGroupId()), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        EffectiveEnvironment environment = environmentResolver.resolve(project.getId(), group.getId(), workflow.getId());
        ExecutionContext context = new ExecutionContext(environment, new LinkedHashMap<>(),
                safeMap(command.inputs()), objectMapper);
        return executeWorkflow(null, workflow, context, listener, cancelled);
    }

    private ExecutionResult executeProject(String projectExecutionId, ProjectExecutionCommand command,
                                           ExecutionListener listener, AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        ProjectEntity project = required(projectMapper.selectById(command.projectId()), "项目不存在");
        emit(listener, EventType.PROJECT_STARTED, projectExecutionId, String.valueOf(project.getId()), project.getName());
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
                        new GroupExecutionCommand(group.getId(), safeMap(command.inputs())), listener, cancelled);
                if (groupResult.status() != Status.PASSED) {
                    finalStatus = groupResult.status();
                    errors.addAll(groupResult.errors());
                    break;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            emit(listener, EventType.EXECUTION_CANCELLED, projectExecutionId, String.valueOf(project.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        emit(listener, EventType.PROJECT_COMPLETED, projectExecutionId, String.valueOf(project.getId()), finalStatus.name());
        return new ExecutionResult(projectExecutionId, finalStatus, elapsed, Map.of(), List.copyOf(errors));
    }

    private ExecutionResult executeGroup(GroupExecutionCommand command,
                                         ExecutionListener listener, AtomicBoolean cancelled) {
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
        emit(listener, EventType.GROUP_STARTED, eventId, String.valueOf(group.getId()), group.getName());
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            runHook(group.getId(), HookType.BEFORE_GROUP, groupExecutionId,
                    null, groupContext, true, listener, cancelled);
            List<WorkflowEntity> workflows = workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                    .eq(WorkflowEntity::getGroupId, group.getId()).eq(WorkflowEntity::getEnabled, true)
                    .orderByAsc(WorkflowEntity::getSortOrder));
            for (WorkflowEntity workflow : workflows) {
                checkCancelled(cancelled);
                EffectiveEnvironment workflowEnvironment = environmentResolver.resolve(project.getId(), group.getId(), workflow.getId());
                ExecutionContext workflowContext = new ExecutionContext(workflowEnvironment, groupVariables, Map.of(), objectMapper);
                ExecutionResult result = executeWorkflow(groupExecutionId, workflow, workflowContext, listener, cancelled);
                if (result.status() != Status.PASSED) {
                    finalStatus = mergeStatus(finalStatus, result.status());
                    errors.addAll(result.errors());
                    if (result.status() == Status.CANCELLED) break;
                }
            }
            runHook(group.getId(), HookType.AFTER_GROUP, groupExecutionId,
                    null, groupContext, true, listener, cancelled);
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            emit(listener, EventType.EXECUTION_CANCELLED, eventId, String.valueOf(group.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED; errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        execution.setStatus(finalStatus.name()); execution.setFinishedAt(LocalDateTime.now()); execution.setElapsedMs(elapsed);
        execution.setContextSnapshot(json(groupVariables)); execution.setErrorMessage(String.join("; ", errors));
        groupExecutionMapper.updateById(execution);
        emit(listener, EventType.GROUP_COMPLETED, eventId, String.valueOf(group.getId()), finalStatus.name());
        return new ExecutionResult(eventId, finalStatus, elapsed, Map.copyOf(groupVariables), List.copyOf(errors));
    }

    private ExecutionResult executeWorkflow(Long groupExecutionId, WorkflowEntity workflow,
                                            ExecutionContext context, ExecutionListener listener,
                                            AtomicBoolean cancelled) {
        long startNanos = System.nanoTime();
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setGroupExecutionId(groupExecutionId); execution.setWorkflowId(workflow.getId());
        execution.setStatus(Status.RUNNING.name()); execution.setStartedAt(LocalDateTime.now());
        execution.setInputJson("{}"); execution.setEnvironmentSnapshot(json(context.environment()));
        List<StepEntity> steps = stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                .eq(StepEntity::getWorkflowId, workflow.getId()).eq(StepEntity::getEnabled, true)
                .orderByAsc(StepEntity::getSortOrder));
        execution.setWorkflowSnapshot(json(Map.of("workflow", workflow, "steps", steps)));
        workflowExecutionMapper.insert(execution);
        Long workflowExecutionId = execution.getId();
        String eventId = String.valueOf(workflowExecutionId);
        emit(listener, EventType.WORKFLOW_STARTED, eventId, String.valueOf(workflow.getId()), workflow.getName());
        List<String> errors = new ArrayList<>();
        Status finalStatus = Status.PASSED;
        try {
            for (StepEntity entity : steps) {
                checkCancelled(cancelled);
                RuntimeStep step = runtime(entity);
                try {
                    runStep(step, context, false, workflowExecutionId, null, listener, cancelled);
                } catch (Throwable e) {
                    errors.add(step.name() + ": " + message(e));
                    throw e;
                }
            }
        } catch (CancellationException e) {
            finalStatus = Status.CANCELLED; errors.add(e.getMessage());
            emit(listener, EventType.EXECUTION_CANCELLED, eventId, String.valueOf(workflow.getId()), e.getMessage());
        } catch (Throwable e) {
            finalStatus = Status.FAILED;
            if (errors.isEmpty()) errors.add(message(e));
        }
        long elapsed = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        execution.setStatus(finalStatus.name()); execution.setFinishedAt(LocalDateTime.now()); execution.setElapsedMs(elapsed);
        execution.setContextSnapshot(json(context.visibleVariables())); execution.setErrorMessage(String.join("; ", errors));
        workflowExecutionMapper.updateById(execution);
        emit(listener, EventType.WORKFLOW_COMPLETED, eventId, String.valueOf(workflow.getId()), finalStatus.name());
        return new ExecutionResult(eventId, finalStatus, elapsed, context.visibleVariables(), List.copyOf(errors));
    }

    private void runHook(Long groupId, HookType hookType,
                         Long groupExecutionId, Long workflowExecutionId,
                         ExecutionContext context, boolean groupScope,
                         ExecutionListener listener, AtomicBoolean cancelled) throws Throwable {
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
        emit(listener, EventType.HOOK_STARTED, parentEventId, hookType.name(), hookType.name());
        long start = System.nanoTime();
        try {
            List<HookStepEntity> steps = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                    .eq(HookStepEntity::getHookId, hook.getId()).eq(HookStepEntity::getEnabled, true)
                    .orderByAsc(HookStepEntity::getSortOrder));
            for (HookStepEntity entity : steps) {
                checkCancelled(cancelled);
                runStep(runtime(entity), context, groupScope, null, hookExecutionId, listener, cancelled);
            }
            execution.setStatus(Status.PASSED.name());
            emit(listener, EventType.HOOK_PASSED, parentEventId, hookType.name(), "钩子成功");
        } catch (Throwable e) {
            execution.setStatus(Status.FAILED.name()); execution.setErrorMessage(message(e));
            emit(listener, EventType.HOOK_FAILED, parentEventId, hookType.name(), message(e));
            throw e;
        } finally {
            execution.setFinishedAt(LocalDateTime.now());
            execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - start).toMillis());
            execution.setOutputJson(json(context.visibleVariables())); hookExecutionMapper.updateById(execution);
        }
    }

    private void runStep(RuntimeStep step, ExecutionContext context, boolean groupScope,
                         Long workflowExecutionId, Long hookExecutionId,
                         ExecutionListener listener, AtomicBoolean cancelled) throws Throwable {
        String parentEventId = String.valueOf(workflowExecutionId != null ? workflowExecutionId : hookExecutionId);
        emit(listener, EventType.STEP_STARTED, parentEventId, step.code(), step.name());
        StepExecutionEntity execution = new StepExecutionEntity();
        execution.setExecutionId(workflowExecutionId);
        execution.setHookExecutionId(hookExecutionId); execution.setStepId(step.id()); execution.setStepCode(step.code());
        execution.setPhase(hookExecutionId == null ? "WORKFLOW" : hookExecutionMapper.selectById(hookExecutionId).getHookType());
        execution.setStatus(Status.RUNNING.name());
        execution.setStartedAt(LocalDateTime.now()); stepExecutionMapper.insert(execution);
        long start = System.nanoTime();
        try {
            checkCancelled(cancelled);
            JsonNode config = templateResolver.resolve(objectMapper.readTree(step.configJson()), context);
            JsonNode extractions = templateResolver.resolve(readArray(step.extractionJson()), context);
            JsonNode assertions = templateResolver.resolve(readArray(step.assertionJson()), context);
            StepResult result = executeStep(step, config, context);
            JsonNode extracted = postProcessor.extract(extractions, result, context, groupScope);
            JsonNode assertionResults = postProcessor.assertAll(assertions, result);
            context.putStepResult(step.code(), result);
            execution.setStatus(Status.PASSED.name()); execution.setRequestJson(json(result.request()));
            execution.setResponseJson(json(result.response())); execution.setOutputJson(json(result.output()));
            execution.setExtractedJson(json(extracted)); execution.setAssertionJson(json(assertionResults));
            emit(listener, EventType.STEP_PASSED, parentEventId, step.code(), step.name());
        } catch (Throwable e) {
            execution.setStatus(Status.FAILED.name()); execution.setErrorMessage(message(e));
            emit(listener, EventType.STEP_FAILED, parentEventId, step.code(), message(e));
            throw e;
        } finally {
            execution.setFinishedAt(LocalDateTime.now());
            execution.setElapsedMs(Duration.ofNanos(System.nanoTime() - start).toMillis());
            stepExecutionMapper.updateById(execution);
        }
    }

    private StepResult executeStep(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        return executorRegistry.get(step.type()).execute(step, config, context);
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
        if (cancelled.get()) throw new CancellationException("执行已取消");
    }
    private void emit(ExecutionListener listener, EventType type, String id, String code, String message) {
        try { listener.onEvent(new ExecutionEvent(type, id, code, message, LocalDateTime.now())); }
        catch (Exception ignored) { }
    }
    private ExecutionListener safe(ExecutionListener listener) { return listener == null ? event -> {} : listener; }
    private Map<String, Object> safeMap(Map<String, Object> value) { return value == null ? Map.of() : value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "{}"; } }
    private JsonNode readArray(String value) { try { return value == null || value.isBlank() ? objectMapper.createArrayNode() : objectMapper.readTree(value); } catch (Exception e) { throw new IllegalArgumentException("JSON 数组格式无效", e); } }
    private <T> T required(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
    private String message(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static final class CancellationException extends RuntimeException { private CancellationException(String message) { super(message); } }
}

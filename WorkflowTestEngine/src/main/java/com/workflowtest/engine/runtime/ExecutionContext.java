package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.workflowtest.engine.executor.support.RuntimeFileResourceManager;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.support.EngineMessages;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionContext {
    /** 变量作用域前缀 */
    public static final class VariableScope {
        public static final String GLOBAL = "global.";
        public static final String PROJECT = "project.";
        public static final String GROUP = "group.";
        public static final String WORKFLOW = "workflow.";
        public static final String STEPS = "steps.";
        /** 逐层向上解析顺序：工作流 → 组 → 项目 → 全局 */
        public static final List<String> LAYERED_PREFIXES = List.of(WORKFLOW, GROUP, PROJECT, GLOBAL);

        private VariableScope() {}
    }

    /** 文件资源占位前缀，如 {@code file.requestBody} 或 {@code file:123} */
    public static final class FileResource {
        public static final String PREFIX = "file.";
        public static final String ID_PREFIX = "file:";

        private FileResource() {}
    }

    private final EffectiveEnvironment environment;
    private final Map<String, Object> projectVariables;
    private final Map<String, Object> groupVariables;
    private final Map<String, Object> workflowVariables = new LinkedHashMap<>();
    private final Map<String, Object> stepResults = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;
    private final RuntimeFileResourceManager fileResources;

    public ExecutionContext(EffectiveEnvironment environment, Map<String, Object> projectVariables,
                            Map<String, Object> groupVariables, Map<String, Object> workflowInputs,
                            ObjectMapper objectMapper) {
        this(environment, projectVariables, groupVariables, workflowInputs, objectMapper, null);
    }

    public ExecutionContext(EffectiveEnvironment environment, Map<String, Object> projectVariables,
                            Map<String, Object> groupVariables, Map<String, Object> workflowInputs,
                            ObjectMapper objectMapper, RuntimeFileResourceManager fileResources) {
        this.environment = environment;
        this.projectVariables = projectVariables == null ? new LinkedHashMap<>() : projectVariables;
        this.groupVariables = groupVariables == null ? new LinkedHashMap<>() : groupVariables;
        if (workflowInputs != null) {
            this.workflowVariables.putAll(workflowInputs);
        }
        this.objectMapper = objectMapper;
        this.fileResources = fileResources;
    }

    public Integer resolveLayeredInt(String key, JsonNode localConfig) {
        if (localConfig != null) {
            JsonNode local = localConfig.get(key);
            if (local != null && !local.isNull()) {
                return local.asInt();
            }
        }
        for (String prefix : VariableScope.LAYERED_PREFIXES) {
            Object value = resolve(prefix + key);
            if (value != null) {
                return toInt(value);
            }
        }
        return null;
    }

    public Object resolve(String path) {
        if (path.startsWith(FileResource.ID_PREFIX)) {
            return resolveFileById(path.substring(FileResource.ID_PREFIX.length()));
        }
        if (path.startsWith(FileResource.PREFIX)) {
            return resolveFileByName(path.substring(FileResource.PREFIX.length()));
        }
        if (path.startsWith(VariableScope.GLOBAL)) {
            return nested(environment.global(), path.substring(VariableScope.GLOBAL.length()));
        }
        if (path.startsWith(VariableScope.PROJECT)) {
            return resolveScoped(path.substring(VariableScope.PROJECT.length()), projectVariables, environment.project());
        }
        if (path.startsWith(VariableScope.GROUP)) {
            return resolveScoped(path.substring(VariableScope.GROUP.length()), groupVariables, environment.group());
        }
        if (path.startsWith(VariableScope.WORKFLOW)) {
            return resolveScoped(path.substring(VariableScope.WORKFLOW.length()), workflowVariables, environment.workflow());
        }
        if (path.startsWith(VariableScope.STEPS)) {
            return nested(stepResults, path.substring(VariableScope.STEPS.length()));
        }
        return resolveScoped(path, workflowVariables, environment.workflow());
    }

    private Object resolveFileById(String idText) {
        if (fileResources == null) {
            return null;
        }
        try {
            return fileResources.readText(Long.parseLong(idText.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(EngineMessages.FILE_RESOURCE_NOT_FOUND, idText));
        }
    }

    private Object resolveFileByName(String name) {
        if (fileResources == null || name.isBlank()) {
            return null;
        }
        return fileResources.readTextByName(name);
    }

    private Object resolveScoped(String key, Map<String, Object> runtime, Map<String, Object> configured) {
        Object value = nested(runtime, key);
        return value != null ? value : nested(configured, key);
    }

    private Object nested(Object root, String path) {
        if (root == null) {
            return null;
        }
        try {
            return JsonPath.read(objectMapper.writeValueAsString(root), "$." + path);
        } catch (Exception e) {
            return null;
        }
    }

    public void putVariable(String path, Object value, RuntimeVariableScope operationScope) {
        assignVariable(path, value, operationScope);
    }

    public void assignVariable(String target, Object value, RuntimeVariableScope operationScope) {
        RuntimeVariableTarget.Resolved resolved = RuntimeVariableTarget.resolve(target, operationScope);
        runtimeMap(resolved.scope()).put(resolved.key(), value);
    }

    public boolean removeVariable(String target, RuntimeVariableScope operationScope) {
        RuntimeVariableTarget.Resolved resolved = RuntimeVariableTarget.resolve(target, operationScope);
        return runtimeMap(resolved.scope()).remove(resolved.key()) != null;
    }

    public void clearProjectVariables() {
        projectVariables.clear();
    }

    public void clearGroupVariables() {
        groupVariables.clear();
    }

    public void clearWorkflowVariables() {
        workflowVariables.clear();
    }

    private Map<String, Object> runtimeMap(RuntimeVariableScope scope) {
        return switch (scope) {
            case PROJECT -> projectVariables;
            case GROUP -> groupVariables;
            case WORKFLOW -> workflowVariables;
        };
    }

    public void putStepResult(String code, StepResult result) {
        stepResults.put(code, objectMapper.convertValue(result.output(), Object.class));
    }

    public Map<String, Object> visibleVariables() {
        Map<String, Object> values = new LinkedHashMap<>(projectVariables);
        values.putAll(groupVariables);
        values.putAll(workflowVariables);
        return values;
    }

    public Map<String, Object> projectVariables() { return projectVariables; }
    public Map<String, Object> groupVariables() { return groupVariables; }
    public Map<String, Object> workflowVariables() { return workflowVariables; }
    public EffectiveEnvironment environment() { return environment; }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

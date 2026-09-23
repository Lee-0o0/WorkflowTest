package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;
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

    private final EffectiveEnvironment environment;
    private final Map<String, Object> groupVariables;
    private final Map<String, Object> workflowVariables = new LinkedHashMap<>();
    private final Map<String, Object> stepResults = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    public ExecutionContext(EffectiveEnvironment environment, Map<String, Object> groupVariables,
                            Map<String, Object> workflowInputs, ObjectMapper objectMapper) {
        this.environment = environment;
        this.groupVariables = groupVariables == null ? new LinkedHashMap<>() : groupVariables;
        if (workflowInputs != null) {
            this.workflowVariables.putAll(workflowInputs);
        }
        this.objectMapper = objectMapper;
    }

    /**
     * 从局部配置向上逐层查找整型参数：步骤配置 → 工作流 → 组 → 项目 → 全局。
     */
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
        if (path.startsWith(VariableScope.GLOBAL)) return nested(environment.global(), path.substring(VariableScope.GLOBAL.length()));
        if (path.startsWith(VariableScope.PROJECT)) return nested(environment.project(), path.substring(VariableScope.PROJECT.length()));
        if (path.startsWith(VariableScope.GROUP)) return resolveScoped(path.substring(VariableScope.GROUP.length()), groupVariables, environment.group());
        if (path.startsWith(VariableScope.WORKFLOW)) return resolveScoped(path.substring(VariableScope.WORKFLOW.length()), workflowVariables, environment.workflow());
        if (path.startsWith(VariableScope.STEPS)) return nested(stepResults, path.substring(VariableScope.STEPS.length()));
        return resolveScoped(path, workflowVariables, environment.workflow());
    }

    private Object resolveScoped(String key, Map<String, Object> runtime, Map<String, Object> configured) {
        Object value = nested(runtime, key);
        return value != null ? value : nested(configured, key);
    }

    private Object nested(Object root, String path) {
        if (root == null) return null;
        try {
            return JsonPath.read(objectMapper.writeValueAsString(root), "$." + path);
        } catch (Exception e) {
            return null;
        }
    }

    public void putVariable(String path, Object value, boolean groupScope) {
        String prefix = groupScope ? VariableScope.GROUP : VariableScope.WORKFLOW;
        if (!path.startsWith(prefix)) {
            throw new IllegalArgumentException(String.format(EngineMessages.EXTRACTION_TARGET_PREFIX, prefix));
        }
        String key = path.substring(prefix.length());
        if (key.contains(".") || key.contains("[")) {
            throw new IllegalArgumentException(EngineMessages.EXTRACTION_TARGET_SINGLE_LEVEL);
        }
        (groupScope ? groupVariables : workflowVariables).put(key, value);
    }

    public void putStepResult(String code, StepResult result) {
        stepResults.put(code, objectMapper.convertValue(result.output(), Object.class));
    }

    public Map<String, Object> visibleVariables() {
        Map<String, Object> values = new LinkedHashMap<>(groupVariables);
        values.putAll(workflowVariables);
        return values;
    }

    public Map<String, Object> groupVariables() { return groupVariables; }
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

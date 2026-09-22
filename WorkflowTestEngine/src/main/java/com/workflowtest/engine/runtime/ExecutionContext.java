package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionContext {
    private final EffectiveEnvironment environment;
    private final Map<String, Object> groupVariables;
    private final Map<String, Object> workflowVariables = new LinkedHashMap<>();
    private final Map<String, Object> stepResults = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    public ExecutionContext(EffectiveEnvironment environment, Map<String, Object> groupVariables,
                            Map<String, Object> workflowInputs, ObjectMapper objectMapper) {
        this.environment = environment;
        this.groupVariables = groupVariables == null ? new LinkedHashMap<>() : groupVariables;
        if (workflowInputs != null) this.workflowVariables.putAll(workflowInputs);
        this.objectMapper = objectMapper;
    }

    public Object resolve(String path) {
        if (path.startsWith("global.")) return nested(environment.global(), path.substring(7));
        if (path.startsWith("project.")) return nested(environment.project(), path.substring(8));
        if (path.startsWith("group.")) return resolveScoped(path.substring(6), groupVariables, environment.group());
        if (path.startsWith("workflow.")) return resolveScoped(path.substring(9), workflowVariables, environment.workflow());
        if (path.startsWith("steps.")) return nested(stepResults, path.substring(6));
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
        String prefix = groupScope ? "group." : "workflow.";
        if (!path.startsWith(prefix)) {
            throw new IllegalArgumentException("提取目标必须以 " + prefix + " 开头");
        }
        String key = path.substring(prefix.length());
        if (key.contains(".") || key.contains("[")) throw new IllegalArgumentException("一期提取目标仅支持单层变量名");
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
}

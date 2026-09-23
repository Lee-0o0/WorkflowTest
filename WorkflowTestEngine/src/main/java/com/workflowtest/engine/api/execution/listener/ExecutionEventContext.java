package com.workflowtest.engine.api.execution.listener;

import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;

import java.util.Map;

/**
 * 执行事件附带的上下文快照：环境、运行时变量及扩展属性。
 */
public record ExecutionEventContext(EffectiveEnvironment environment,
                                    Map<String, Object> variables,
                                    Map<String, Object> attributes) {
    public static final ExecutionEventContext EMPTY = new ExecutionEventContext(null, Map.of(), Map.of());

    public static ExecutionEventContext of(EffectiveEnvironment environment,
                                           Map<String, Object> variables,
                                           Map<String, Object> attributes) {
        return new ExecutionEventContext(
                environment,
                copyMap(variables),
                copyMap(attributes)
        );
    }

    public static ExecutionEventContext environmentOnly(EffectiveEnvironment environment) {
        return of(environment, Map.of(), Map.of());
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }

    public Long longAttribute(String key) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}

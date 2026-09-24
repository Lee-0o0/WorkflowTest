package com.workflowtest.engine.runtime;

import com.workflowtest.engine.support.EngineMessages;

/**
 * 解析运行时环境变量的赋值/删除/提取目标。
 */
public final class RuntimeVariableTarget {
    public record Resolved(RuntimeVariableScope scope, String key) {}

    private RuntimeVariableTarget() {}

    public static Resolved resolve(String target, RuntimeVariableScope operationScope) {
        if (target == null || target.isBlank() || hasForbiddenPrefix(target)) {
            throw new IllegalArgumentException(String.format(EngineMessages.ENV_VARIABLE_INVALID, target));
        }
        if (target.startsWith(ExecutionContext.VariableScope.GROUP)) {
            requireScope(operationScope, RuntimeVariableScope.GROUP, target);
            return new Resolved(RuntimeVariableScope.GROUP, stripKey(ExecutionContext.VariableScope.GROUP, target));
        }
        if (target.startsWith(ExecutionContext.VariableScope.PROJECT)) {
            requireScope(operationScope, RuntimeVariableScope.PROJECT, target);
            return new Resolved(RuntimeVariableScope.PROJECT, stripKey(ExecutionContext.VariableScope.PROJECT, target));
        }
        if (target.startsWith(ExecutionContext.VariableScope.WORKFLOW)) {
            requireScope(operationScope, RuntimeVariableScope.WORKFLOW, target);
            return new Resolved(RuntimeVariableScope.WORKFLOW, stripKey(ExecutionContext.VariableScope.WORKFLOW, target));
        }
        validateSingleLevelKey(target);
        return new Resolved(operationScope, target);
    }

    private static void requireScope(RuntimeVariableScope operationScope, RuntimeVariableScope expected, String target) {
        if (operationScope != expected) {
            throw new IllegalArgumentException(switch (operationScope) {
                case WORKFLOW -> String.format(EngineMessages.RUNTIME_VAR_WORKFLOW_TARGET_ONLY, target);
                case GROUP -> String.format(EngineMessages.RUNTIME_VAR_GROUP_TARGET_ONLY, target);
                case PROJECT -> String.format(EngineMessages.RUNTIME_VAR_PROJECT_TARGET_ONLY, target);
            });
        }
    }

    private static String stripKey(String prefix, String target) {
        String key = target.substring(prefix.length());
        validateSingleLevelKey(key);
        return key;
    }

    private static void validateSingleLevelKey(String key) {
        if (key.contains(".") || key.contains("[")) {
            throw new IllegalArgumentException(EngineMessages.EXTRACTION_TARGET_SINGLE_LEVEL);
        }
    }

    private static boolean hasForbiddenPrefix(String target) {
        return target.startsWith(ExecutionContext.VariableScope.GLOBAL)
                || target.startsWith(ExecutionContext.VariableScope.STEPS);
    }
}

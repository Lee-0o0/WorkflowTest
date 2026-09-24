package com.workflowtest.engine.model.plan;

import com.workflowtest.engine.runtime.RuntimeStep;

import java.util.List;

/**
 * 钩子执行计划。
 */
public record HookPlan(Long id, List<RuntimeStep> steps) {
    public boolean isEmpty() {
        return steps == null || steps.isEmpty();
    }
}

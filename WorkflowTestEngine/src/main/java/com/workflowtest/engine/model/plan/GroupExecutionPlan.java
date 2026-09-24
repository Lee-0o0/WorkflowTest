package com.workflowtest.engine.model.plan;

import com.workflowtest.engine.model.EffectiveEnvironment;

import java.util.List;

/**
 * 工作流组执行计划。
 */
public record GroupExecutionPlan(
        PlanEntityRef group,
        EffectiveEnvironment environment,
        RuntimeProjectResources resources,
        HookPlan beforeGroup,
        HookPlan afterGroup,
        List<WorkflowExecutionPlan> workflows) {
}

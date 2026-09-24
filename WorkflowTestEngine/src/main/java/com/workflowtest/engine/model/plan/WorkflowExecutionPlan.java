package com.workflowtest.engine.model.plan;

import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.runtime.RuntimeStep;

import java.util.List;

/**
 * 单工作流执行计划。
 */
public record WorkflowExecutionPlan(
        PlanEntityRef workflow,
        EffectiveEnvironment environment,
        RuntimeProjectResources resources,
        List<RuntimeStep> steps) {
}

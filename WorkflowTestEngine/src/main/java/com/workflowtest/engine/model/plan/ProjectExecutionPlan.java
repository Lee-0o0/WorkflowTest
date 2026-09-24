package com.workflowtest.engine.model.plan;

import com.workflowtest.engine.model.EffectiveEnvironment;

import java.util.List;

/**
 * 项目级执行计划。
 */
public record ProjectExecutionPlan(
        PlanEntityRef project,
        EffectiveEnvironment environment,
        RuntimeProjectResources resources,
        HookPlan beforeEachGroup,
        List<GroupExecutionPlan> groups) {
}

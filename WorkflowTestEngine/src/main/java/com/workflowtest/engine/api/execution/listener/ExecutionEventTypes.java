package com.workflowtest.engine.api.execution.listener;

import java.util.EnumSet;
import java.util.Set;

/**
 * 执行事件类型分组常量。
 */
public final class ExecutionEventTypes {
    private ExecutionEventTypes() {}

    public static final Set<ExecutionEventType> PROJECT = EnumSet.of(
            ExecutionEventType.PROJECT_STARTED, ExecutionEventType.PROJECT_COMPLETED);

    public static final Set<ExecutionEventType> GROUP = EnumSet.of(
            ExecutionEventType.GROUP_STARTED, ExecutionEventType.GROUP_COMPLETED);

    public static final Set<ExecutionEventType> WORKFLOW = EnumSet.of(
            ExecutionEventType.WORKFLOW_STARTED, ExecutionEventType.WORKFLOW_COMPLETED);

    public static final Set<ExecutionEventType> HOOK = EnumSet.of(
            ExecutionEventType.HOOK_STARTED, ExecutionEventType.HOOK_PASSED, ExecutionEventType.HOOK_FAILED);

    public static final Set<ExecutionEventType> STEP = EnumSet.of(
            ExecutionEventType.STEP_STARTED, ExecutionEventType.STEP_PASSED, ExecutionEventType.STEP_FAILED);

    public static final Set<ExecutionEventType> LIFECYCLE = EnumSet.of(
            ExecutionEventType.EXECUTION_CANCELLED);
}

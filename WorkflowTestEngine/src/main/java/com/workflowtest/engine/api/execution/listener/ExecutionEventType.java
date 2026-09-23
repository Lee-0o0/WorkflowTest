package com.workflowtest.engine.api.execution.listener;

/**
 * 执行过程事件类型。
 */
public enum ExecutionEventType {
    PROJECT_STARTED,
    PROJECT_COMPLETED,
    GROUP_STARTED,
    GROUP_COMPLETED,
    WORKFLOW_STARTED,
    WORKFLOW_COMPLETED,
    HOOK_STARTED,
    HOOK_PASSED,
    HOOK_FAILED,
    STEP_STARTED,
    STEP_PASSED,
    STEP_FAILED,
    EXECUTION_CANCELLED
}

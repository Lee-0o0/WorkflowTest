package com.workflowtest.engine.api.execution.listener;

/**
 * {@link ExecutionEventContext#attributes()} 中的常用键名。
 */
public final class ExecutionEventAttributes {
    public static final String PROJECT_ID = "projectId";
    public static final String GROUP_ID = "groupId";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String GROUP_EXECUTION_ID = "groupExecutionId";
    public static final String WORKFLOW_EXECUTION_ID = "workflowExecutionId";
    public static final String HOOK_EXECUTION_ID = "hookExecutionId";
    public static final String HOOK_TYPE = "hookType";
    public static final String STEP_CODE = "stepCode";
    public static final String STEP_NAME = "stepName";
    public static final String STATUS = "status";

    private ExecutionEventAttributes() {}
}

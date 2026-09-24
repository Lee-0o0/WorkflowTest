package com.workflowtest.engine.listener;

/**
 * 按事件类型分派的监听器适配器，子类可覆盖 {@link #supports} 与具体事件回调。
 */
public abstract class ExecutionListenerAdapter implements ExecutionListener {
    @Override
    public boolean supports(ExecutionEvent event) {
        return true;
    }

    @Override
    public final void onEvent(ExecutionEvent event) {
        if (!supports(event)) {
            return;
        }
        switch (event.type()) {
            case PROJECT_STARTED -> onProjectStarted(event);
            case PROJECT_COMPLETED -> onProjectCompleted(event);
            case GROUP_STARTED -> onGroupStarted(event);
            case GROUP_COMPLETED -> onGroupCompleted(event);
            case WORKFLOW_STARTED -> onWorkflowStarted(event);
            case WORKFLOW_COMPLETED -> onWorkflowCompleted(event);
            case HOOK_STARTED -> onHookStarted(event);
            case HOOK_PASSED -> onHookPassed(event);
            case HOOK_FAILED -> onHookFailed(event);
            case STEP_STARTED -> onStepStarted(event);
            case STEP_PASSED -> onStepPassed(event);
            case STEP_FAILED -> onStepFailed(event);
            case EXECUTION_CANCELLED -> onExecutionCancelled(event);
        }
    }

    protected void onProjectStarted(ExecutionEvent event) {}

    protected void onProjectCompleted(ExecutionEvent event) {}

    protected void onGroupStarted(ExecutionEvent event) {}

    protected void onGroupCompleted(ExecutionEvent event) {}

    protected void onWorkflowStarted(ExecutionEvent event) {}

    protected void onWorkflowCompleted(ExecutionEvent event) {}

    protected void onHookStarted(ExecutionEvent event) {}

    protected void onHookPassed(ExecutionEvent event) {}

    protected void onHookFailed(ExecutionEvent event) {}

    protected void onStepStarted(ExecutionEvent event) {}

    protected void onStepPassed(ExecutionEvent event) {}

    protected void onStepFailed(ExecutionEvent event) {}

    protected void onExecutionCancelled(ExecutionEvent event) {}
}

package com.workflowtest.engine.api.execution.listener;

/**
 * 仅处理步骤相关事件的监听器基类。
 */
public abstract class StepExecutionListener extends ExecutionListenerAdapter {
    @Override
    public final boolean supports(ExecutionEvent event) {
        return ExecutionEventTypes.STEP.contains(event.type());
    }

    @Override protected final void onProjectStarted(ExecutionEvent event) {}
    @Override protected final void onProjectCompleted(ExecutionEvent event) {}
    @Override protected final void onGroupStarted(ExecutionEvent event) {}
    @Override protected final void onGroupCompleted(ExecutionEvent event) {}
    @Override protected final void onWorkflowStarted(ExecutionEvent event) {}
    @Override protected final void onWorkflowCompleted(ExecutionEvent event) {}
    @Override protected final void onHookStarted(ExecutionEvent event) {}
    @Override protected final void onHookPassed(ExecutionEvent event) {}
    @Override protected final void onHookFailed(ExecutionEvent event) {}
    @Override protected final void onExecutionCancelled(ExecutionEvent event) {}
}

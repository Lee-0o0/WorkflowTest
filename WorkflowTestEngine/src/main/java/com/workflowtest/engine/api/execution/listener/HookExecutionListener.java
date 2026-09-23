package com.workflowtest.engine.api.execution.listener;

/**
 * 仅处理钩子相关事件的监听器基类。
 */
public abstract class HookExecutionListener extends ExecutionListenerAdapter {
    @Override
    public final boolean supports(ExecutionEvent event) {
        return ExecutionEventTypes.HOOK.contains(event.type());
    }

    @Override protected final void onProjectStarted(ExecutionEvent event) {}
    @Override protected final void onProjectCompleted(ExecutionEvent event) {}
    @Override protected final void onGroupStarted(ExecutionEvent event) {}
    @Override protected final void onGroupCompleted(ExecutionEvent event) {}
    @Override protected final void onWorkflowStarted(ExecutionEvent event) {}
    @Override protected final void onWorkflowCompleted(ExecutionEvent event) {}
    @Override protected final void onStepStarted(ExecutionEvent event) {}
    @Override protected final void onStepPassed(ExecutionEvent event) {}
    @Override protected final void onStepFailed(ExecutionEvent event) {}
    @Override protected final void onExecutionCancelled(ExecutionEvent event) {}
}

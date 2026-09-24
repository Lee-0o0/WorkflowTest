package com.workflowtest.engine.application.execution;

import com.workflowtest.engine.listener.ExecutionEvent;
import com.workflowtest.engine.listener.ExecutionEventContext;
import com.workflowtest.engine.listener.ExecutionEventType;
import com.workflowtest.engine.listener.ExecutionListener;
import com.workflowtest.engine.listener.ExecutionListeners;

import java.util.List;

/**
 * 收集 {@link ExecutionListener} 并按 order 广播执行事件。
 */
public class ExecutionListenerPublisher {
    private final List<ExecutionListener> listeners;

    public ExecutionListenerPublisher(List<ExecutionListener> listeners) {
        this.listeners = ExecutionListeners.copyOf(listeners);
    }

    public void notify(ExecutionEventType type, String executionId, String code, String message) {
        ExecutionListeners.notify(listeners, type, executionId, code, message);
    }

    public void notify(ExecutionEventType type, String executionId, String code, String message,
                       ExecutionEventContext context) {
        ExecutionListeners.notify(listeners, type, executionId, code, message, context);
    }

    public void notify(ExecutionEvent event) {
        ExecutionListeners.notify(listeners, event);
    }
}

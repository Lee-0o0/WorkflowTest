package com.workflowtest.engine.application.execution;

import com.workflowtest.engine.api.execution.listener.ExecutionEvent;
import com.workflowtest.engine.api.execution.listener.ExecutionEventType;
import com.workflowtest.engine.api.execution.listener.ExecutionListener;
import com.workflowtest.engine.api.execution.listener.ExecutionListeners;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 从 Spring 容器收集全部 {@link ExecutionListener} Bean，并按 order 广播执行事件。
 * <p>
 * 不限于 {@code com.workflowtest.engine} 包：外部项目只要将监听器注册为 Spring Bean 即可被注入。
 */
@Component
public class ExecutionListenerPublisher {
    private final List<ExecutionListener> listeners;

    public ExecutionListenerPublisher(List<ExecutionListener> listeners) {
        this.listeners = ExecutionListeners.copyOf(listeners);
    }

    public void notify(ExecutionEventType type, String executionId, String code, String message) {
        ExecutionListeners.notify(listeners, type, executionId, code, message);
    }

    public void notify(ExecutionEvent event) {
        ExecutionListeners.notify(listeners, event);
    }
}

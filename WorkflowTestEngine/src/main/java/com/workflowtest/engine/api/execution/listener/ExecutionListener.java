package com.workflowtest.engine.api.execution.listener;

/**
 * 执行过程事件监听器：先 {@link #supports(ExecutionEvent)} 过滤，再 {@link #onEvent(ExecutionEvent)} 处理。
 */
public interface ExecutionListener extends ExecutionListenerSupport, Order {
    void onEvent(ExecutionEvent event);
}

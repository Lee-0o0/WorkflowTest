package com.workflowtest.engine.listener;

/**
 * 判断监听器是否处理指定执行事件。
 */
public interface ExecutionListenerSupport {
    boolean supports(ExecutionEvent event);
}

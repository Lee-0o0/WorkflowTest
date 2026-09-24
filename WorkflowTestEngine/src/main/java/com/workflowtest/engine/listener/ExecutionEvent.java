package com.workflowtest.engine.listener;

import java.time.LocalDateTime;

/**
 * 执行过程事件。
 */
public record ExecutionEvent(ExecutionEventType type, String executionId, String code,
                             String message, LocalDateTime time, ExecutionEventContext context) {
    public static ExecutionEvent of(ExecutionEventType type, String executionId, String code, String message) {
        return of(type, executionId, code, message, ExecutionEventContext.EMPTY);
    }

    public static ExecutionEvent of(ExecutionEventType type, String executionId, String code, String message,
                                    ExecutionEventContext context) {
        return new ExecutionEvent(type, executionId, code, message, LocalDateTime.now(),
                context == null ? ExecutionEventContext.EMPTY : context);
    }
}

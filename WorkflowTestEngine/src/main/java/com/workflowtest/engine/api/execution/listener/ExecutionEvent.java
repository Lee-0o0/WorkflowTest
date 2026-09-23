package com.workflowtest.engine.api.execution.listener;

import java.time.LocalDateTime;

/**
 * 执行过程事件。
 */
public record ExecutionEvent(ExecutionEventType type, String executionId, String code,
                             String message, LocalDateTime time) {}

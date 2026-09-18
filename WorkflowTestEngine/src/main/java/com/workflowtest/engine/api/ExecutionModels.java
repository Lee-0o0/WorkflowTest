package com.workflowtest.engine.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;

public final class ExecutionModels {
    private ExecutionModels() {}

    public enum Status { PENDING, RUNNING, PASSED, FAILED, CANCELLED, TIMEOUT }
    public enum EventType {
        GROUP_STARTED, GROUP_COMPLETED, WORKFLOW_STARTED, WORKFLOW_COMPLETED,
        HOOK_STARTED, HOOK_PASSED, HOOK_FAILED, STEP_STARTED, STEP_PASSED,
        STEP_FAILED, EXECUTION_CANCELLED
    }

    public record WorkflowExecutionCommand(String workflowId, Map<String, Object> inputs) {}
    public record GroupExecutionCommand(String groupId, Map<String, Object> inputs) {}
    public record PackageExecutionCommand(JsonNode executionPackage, Map<String, Object> inputs) {}
    public record ExecutionEvent(EventType type, String executionId, String code,
                                 String message, LocalDateTime time) {}
    @FunctionalInterface public interface ExecutionListener { void onEvent(ExecutionEvent event); }
    public record ExecutionResult(String executionId, Status status, long elapsedMs,
                                  Map<String, Object> variables, List<String> errors) {}
    public record ExecutionHandle(String executionId, CompletableFuture<ExecutionResult> future) {}
}

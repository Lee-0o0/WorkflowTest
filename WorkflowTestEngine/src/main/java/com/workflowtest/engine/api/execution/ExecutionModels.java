package com.workflowtest.engine.api.execution;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;

public final class ExecutionModels {
    private ExecutionModels() {}

    public enum Status { PENDING, RUNNING, PASSED, FAILED, CANCELLED, TIMEOUT }

    public record ProjectExecutionCommand(Long projectId, Map<String, Object> inputs) {}
    public record WorkflowExecutionCommand(Long workflowId, Map<String, Object> inputs) {}
    public record GroupExecutionCommand(Long groupId, Map<String, Object> inputs) {}
    public record PackageExecutionCommand(JsonNode executionPackage, Map<String, Object> inputs) {}
    public record ExecutionResult(String executionId, Status status, long elapsedMs,
                                  Map<String, Object> variables, List<String> errors) {}
    public record ExecutionHandle(String executionId, CompletableFuture<ExecutionResult> future) {}

    public record ExecutionSummary(String id, String type, String targetId, String targetName,
                                   String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                                   Long elapsedMs, String errorMessage) {}

    public record StepExecutionDetail(String id, String stepCode, String phase, String status,
                                      String requestJson, String responseJson, String outputJson,
                                      String extractedJson, String assertionJson, Long elapsedMs,
                                      String errorMessage) {}
}

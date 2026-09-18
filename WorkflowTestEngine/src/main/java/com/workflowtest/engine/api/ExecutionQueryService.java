package com.workflowtest.engine.api;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionQueryService {
    record ExecutionSummary(String id, String type, String targetId, String targetName,
                            String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                            Long elapsedMs, String errorMessage) {}
    record StepExecutionDetail(String id, String stepCode, String phase, String status,
                               String requestJson, String responseJson, String outputJson,
                               String extractedJson, String assertionJson, Long elapsedMs,
                               String errorMessage) {}

    List<ExecutionSummary> recent(int limit);
    List<StepExecutionDetail> steps(String workflowExecutionId);
}

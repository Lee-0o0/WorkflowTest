package com.workflowtest.server.web.controller;

import com.workflowtest.engine.WorkflowEngine.ExecutionHandle;
import com.workflowtest.engine.WorkflowEngine.Status;
import com.workflowtest.server.service.execution.ExecutionHistoryService;
import com.workflowtest.server.service.execution.StepExecutionQueryService;
import com.workflowtest.server.service.impl.execution.ExecutionOrchestrationService;
import com.workflowtest.server.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionOrchestrationService executionOrchestrationService;
    private final ExecutionHistoryService executionHistoryService;
    private final StepExecutionQueryService stepExecutionQueryService;

    @PostMapping("/projects/{projectId}")
    public ApiResponse<?> runProject(@PathVariable Long projectId) {
        return submit(executionOrchestrationService.runProject(projectId));
    }

    @PostMapping("/groups/{groupId}")
    public ApiResponse<?> runGroup(@PathVariable Long groupId) {
        return submit(executionOrchestrationService.runGroup(groupId));
    }

    @PostMapping("/workflows/{workflowId}")
    public ApiResponse<?> runWorkflow(@PathVariable Long workflowId) {
        return submit(executionOrchestrationService.runWorkflow(workflowId));
    }

    @PostMapping("/{executionId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String executionId) {
        executionOrchestrationService.cancel(executionId);
        return ApiResponse.ok();
    }

    @GetMapping("/{executionId}/result")
    public ApiResponse<?> result(@PathVariable String executionId,
                                 @RequestParam(defaultValue = "0") long timeoutMs) throws Exception {
        return ApiResponse.ok(executionOrchestrationService.awaitResult(executionId, timeoutMs));
    }

    @GetMapping("/history")
    public ApiResponse<?> history(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(executionHistoryService.recent(limit));
    }

    @GetMapping("/{executionId}/steps")
    public ApiResponse<?> steps(@PathVariable String executionId) {
        return ApiResponse.ok(stepExecutionQueryService.listByExecution(executionId));
    }

    private ApiResponse<Map<String, Object>> submit(ExecutionHandle handle) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", handle.executionId());
        payload.put("status", Status.RUNNING.name());
        return ApiResponse.ok(payload);
    }
}

package com.workflowtest.server.service.impl.execution;

import com.workflowtest.engine.WorkflowEngine;
import com.workflowtest.engine.WorkflowEngine.ExecutionHandle;
import com.workflowtest.engine.WorkflowEngine.ExecutionResult;
import com.workflowtest.server.execution.ExecutionHandleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ExecutionOrchestrationService {
    private final WorkflowEngine workflowEngine;
    private final ExecutionPlanAssembler planAssembler;
    private final ExecutionHandleRegistry handleRegistry;

    public ExecutionHandle runProject(Long projectId) {
        return submit(workflowEngine.execute(planAssembler.buildProjectPlan(projectId)));
    }

    public ExecutionHandle runGroup(Long groupId) {
        return submit(workflowEngine.execute(planAssembler.buildGroupPlan(groupId)));
    }

    public ExecutionHandle runWorkflow(Long workflowId) {
        return submit(workflowEngine.execute(planAssembler.buildWorkflowPlan(workflowId)));
    }

    public void cancel(String executionId) {
        workflowEngine.cancel(executionId);
    }

    public ExecutionResult awaitResult(String executionId, long timeoutMs) throws Exception {
        CompletableFuture<ExecutionResult> future = handleRegistry.get(executionId);
        if (future == null) {
            throw new IllegalArgumentException("执行不存在或已结束");
        }
        return timeoutMs <= 0 ? future.get() : future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private ExecutionHandle submit(ExecutionHandle handle) {
        handleRegistry.register(handle);
        return handle;
    }
}

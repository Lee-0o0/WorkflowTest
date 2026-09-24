package com.workflowtest.server.execution;

import com.workflowtest.engine.WorkflowEngine.ExecutionHandle;
import com.workflowtest.engine.WorkflowEngine.ExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionHandleRegistry {
    private final Map<String, CompletableFuture<ExecutionResult>> active = new ConcurrentHashMap<>();

    public void register(ExecutionHandle handle) {
        active.put(handle.executionId(), handle.future());
        handle.future().whenComplete((result, error) -> active.remove(handle.executionId()));
    }

    public CompletableFuture<ExecutionResult> get(String executionId) {
        return active.get(executionId);
    }
}

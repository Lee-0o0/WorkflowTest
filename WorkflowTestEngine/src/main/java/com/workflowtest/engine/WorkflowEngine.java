package com.workflowtest.engine;

import com.workflowtest.engine.model.plan.GroupExecutionPlan;
import com.workflowtest.engine.model.plan.ProjectExecutionPlan;
import com.workflowtest.engine.model.plan.WorkflowExecutionPlan;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

/**
 * 工作流测试引擎唯一入口：根据 Server 组装的执行计划运行测试。
 */
public interface WorkflowEngine {

    ExecutionHandle execute(ProjectExecutionPlan plan);

    ExecutionHandle execute(GroupExecutionPlan plan);

    ExecutionHandle execute(WorkflowExecutionPlan plan);

    void cancel(String executionId);

    enum Status {
        PENDING, RUNNING, PASSED, FAILED, CANCELLED, TIMEOUT
    }

    record ExecutionHandle(String executionId, CompletableFuture<ExecutionResult> future) {}

    record ExecutionResult(
            String executionId,
            Status status,
            long elapsedMs,
            Map<String, Object> variables,
            List<String> errors) {}
}

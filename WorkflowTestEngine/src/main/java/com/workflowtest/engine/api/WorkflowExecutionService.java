package com.workflowtest.engine.api;

import com.workflowtest.engine.api.ExecutionModels.*;

public interface WorkflowExecutionService {
    ExecutionHandle submitWorkflow(WorkflowExecutionCommand command, ExecutionListener listener);
    ExecutionHandle submitGroup(GroupExecutionCommand command, ExecutionListener listener);
    ExecutionHandle submitPackage(PackageExecutionCommand command, ExecutionListener listener);
    void cancel(String executionId);
}

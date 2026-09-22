package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

public interface WorkflowRunService {
    ExecutionHandle submit(WorkflowExecutionCommand command, ExecutionListener listener);
}

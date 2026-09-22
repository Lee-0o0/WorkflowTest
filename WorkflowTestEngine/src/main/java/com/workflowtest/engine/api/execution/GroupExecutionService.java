package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

public interface GroupExecutionService {
    ExecutionHandle submit(GroupExecutionCommand command, ExecutionListener listener);
}

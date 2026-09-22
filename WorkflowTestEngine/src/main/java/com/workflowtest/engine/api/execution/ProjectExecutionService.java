package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

public interface ProjectExecutionService {
    ExecutionHandle submit(ProjectExecutionCommand command, ExecutionListener listener);
}

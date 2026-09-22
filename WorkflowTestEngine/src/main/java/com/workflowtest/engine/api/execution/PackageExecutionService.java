package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

public interface PackageExecutionService {
    ExecutionHandle submit(PackageExecutionCommand command, ExecutionListener listener);
}

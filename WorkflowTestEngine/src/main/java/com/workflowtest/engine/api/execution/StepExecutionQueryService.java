package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.StepExecutionDetail;

import java.util.List;

public interface StepExecutionQueryService {
    List<StepExecutionDetail> listByExecution(String executionId);
}

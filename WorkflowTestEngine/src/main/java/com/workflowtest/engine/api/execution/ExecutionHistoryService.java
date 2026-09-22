package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.ExecutionSummary;

import java.util.List;

public interface ExecutionHistoryService {
    List<ExecutionSummary> recent(int limit);
}

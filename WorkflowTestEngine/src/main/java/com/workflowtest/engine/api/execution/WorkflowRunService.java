package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

/**
 * 工作流执行接口
 */
public interface WorkflowRunService {
    /**
     * 提交单个工作流执行
     * @param command 执行命令，包含工作流主键与输入参数
     * @return 执行句柄，可通过 {@code future} 获取最终结果
     */
    ExecutionHandle submit(WorkflowExecutionCommand command);
}

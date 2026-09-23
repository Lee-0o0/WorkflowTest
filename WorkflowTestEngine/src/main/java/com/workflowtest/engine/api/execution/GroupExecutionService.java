package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

/**
 * 组执行接口
 */
public interface GroupExecutionService {
    /**
     * 提交组执行（组前置钩子 → 组内工作流串行 → 组后置钩子）
     * @param command 执行命令，包含组主键与输入参数
     * @return 执行句柄，可通过 {@code future} 获取最终结果
     */
    ExecutionHandle submit(GroupExecutionCommand command);
}

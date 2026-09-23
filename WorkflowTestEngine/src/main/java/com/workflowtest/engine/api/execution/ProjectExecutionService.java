package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

/**
 * 项目执行接口
 */
public interface ProjectExecutionService {
    /**
     * 提交项目执行（按组顺序串行执行项目下全部组）
     * @param command 执行命令，包含项目主键与输入参数
     * @return 执行句柄，可通过 {@code future} 获取最终结果
     */
    ExecutionHandle submit(ProjectExecutionCommand command);
}

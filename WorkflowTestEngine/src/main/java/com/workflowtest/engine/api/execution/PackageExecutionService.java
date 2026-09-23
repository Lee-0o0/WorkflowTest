package com.workflowtest.engine.api.execution;

import com.workflowtest.engine.api.execution.ExecutionModels.*;

/**
 * 远程执行包接口
 */
public interface PackageExecutionService {
    /**
     * 提交远程工作流包执行（不依赖本地持久化定义）
     * @param command 执行命令，包含工作流定义包与输入参数
     * @return 执行句柄，可通过 {@code future} 获取最终结果
     */
    ExecutionHandle submit(PackageExecutionCommand command);
}

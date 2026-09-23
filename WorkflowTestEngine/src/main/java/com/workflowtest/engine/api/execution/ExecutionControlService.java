package com.workflowtest.engine.api.execution;

/**
 * 执行控制接口
 */
public interface ExecutionControlService {
    /**
     * 取消正在运行的执行
     * @param executionId 执行主键
     */
    void cancel(String executionId);
}

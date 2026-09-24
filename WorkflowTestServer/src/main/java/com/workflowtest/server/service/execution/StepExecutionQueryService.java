package com.workflowtest.server.service.execution;

import com.workflowtest.server.service.execution.ExecutionModels.StepExecutionDetail;

import java.util.List;

/**
 * 步骤执行记录查询接口
 */
public interface StepExecutionQueryService {
    /**
     * 查询某次执行下的全部步骤记录
     * @param executionId 工作流或组执行主键
     * @return 步骤执行详情列表
     */
    List<StepExecutionDetail> listByExecution(String executionId);
}

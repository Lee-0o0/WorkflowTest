package com.workflowtest.server.service.execution;

import com.workflowtest.server.service.execution.ExecutionModels.ExecutionSummary;

import java.util.List;

/**
 * 执行历史查询接口
 */
public interface ExecutionHistoryService {
    /**
     * 查询最近的执行记录
     * @param limit 返回条数上限
     * @return 执行摘要列表，按开始时间倒序
     */
    List<ExecutionSummary> recent(int limit);
}

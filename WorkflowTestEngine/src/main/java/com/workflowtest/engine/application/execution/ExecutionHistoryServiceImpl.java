package com.workflowtest.engine.application.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.execution.ExecutionHistoryService;
import com.workflowtest.engine.api.execution.ExecutionModels.ExecutionSummary;
import com.workflowtest.engine.persistence.entity.GroupExecutionEntity;
import com.workflowtest.engine.persistence.entity.WorkflowExecutionEntity;
import com.workflowtest.engine.persistence.mapper.GroupExecutionMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowExecutionMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowGroupMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowMapper;
import com.workflowtest.engine.support.CommonConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionHistoryServiceImpl implements ExecutionHistoryService {
    private static final String TYPE_GROUP = "GROUP";
    private static final String TYPE_WORKFLOW = "WORKFLOW";
    private static final int HISTORY_LIMIT_MAX = 500;

    private final GroupExecutionMapper groupExecutions;
    private final WorkflowExecutionMapper workflowExecutions;
    private final WorkflowGroupMapper groups;
    private final WorkflowMapper workflows;

    @Override
    public List<ExecutionSummary> recent(int limit) {
        int capped = Math.max(CommonConstant.ONE, Math.min(limit, HISTORY_LIMIT_MAX));
        List<ExecutionSummary> result = new ArrayList<>();
        groupExecutions.selectList(Wrappers.<GroupExecutionEntity>lambdaQuery()
                        .orderByDesc(GroupExecutionEntity::getStartedAt).last("LIMIT " + capped))
                .forEach(e -> result.add(new ExecutionSummary(String.valueOf(e.getId()), TYPE_GROUP,
                        String.valueOf(e.getGroupId()), nameOfGroup(e.getGroupId()), e.getStatus(),
                        e.getStartedAt(), e.getFinishedAt(), e.getElapsedMs(), e.getErrorMessage())));
        workflowExecutions.selectList(Wrappers.<WorkflowExecutionEntity>lambdaQuery()
                        .orderByDesc(WorkflowExecutionEntity::getStartedAt).last("LIMIT " + capped))
                .forEach(e -> result.add(new ExecutionSummary(String.valueOf(e.getId()), TYPE_WORKFLOW,
                        String.valueOf(e.getWorkflowId()), nameOfWorkflow(e.getWorkflowId()), e.getStatus(),
                        e.getStartedAt(), e.getFinishedAt(), e.getElapsedMs(), e.getErrorMessage())));
        return result.stream().sorted(Comparator.comparing(ExecutionSummary::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(capped).toList();
    }

    private String nameOfGroup(Long id) {
        var entity = groups.selectById(id);
        return entity == null ? String.valueOf(id) : entity.getName();
    }

    private String nameOfWorkflow(Long id) {
        var entity = workflows.selectById(id);
        return entity == null ? String.valueOf(id) : entity.getName();
    }
}

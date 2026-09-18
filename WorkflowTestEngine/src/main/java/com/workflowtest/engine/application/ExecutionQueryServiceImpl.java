package com.workflowtest.engine.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.ExecutionQueryService;
import com.workflowtest.engine.persistence.entity.GroupExecutionEntity;
import com.workflowtest.engine.persistence.entity.StepExecutionEntity;
import com.workflowtest.engine.persistence.entity.WorkflowExecutionEntity;
import com.workflowtest.engine.persistence.entity.HookExecutionEntity;
import com.workflowtest.engine.persistence.mapper.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ExecutionQueryServiceImpl implements ExecutionQueryService {
    private final GroupExecutionMapper groupExecutions;
    private final WorkflowExecutionMapper workflowExecutions;
    private final StepExecutionMapper stepExecutions;
    private final HookExecutionMapper hookExecutions;
    private final WorkflowGroupMapper groups;
    private final WorkflowMapper workflows;

    public ExecutionQueryServiceImpl(GroupExecutionMapper groupExecutions,
                                     WorkflowExecutionMapper workflowExecutions,
                                     StepExecutionMapper stepExecutions,
                                     HookExecutionMapper hookExecutions,
                                     WorkflowGroupMapper groups, WorkflowMapper workflows) {
        this.groupExecutions = groupExecutions; this.workflowExecutions = workflowExecutions;
        this.stepExecutions = stepExecutions; this.groups = groups; this.workflows = workflows;
        this.hookExecutions = hookExecutions;
    }

    @Override
    public List<ExecutionSummary> recent(int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        List<ExecutionSummary> result = new ArrayList<>();
        groupExecutions.selectList(Wrappers.<GroupExecutionEntity>lambdaQuery()
                        .orderByDesc(GroupExecutionEntity::getStartedAt).last("LIMIT " + capped))
                .forEach(e -> result.add(new ExecutionSummary(e.getId(), "GROUP", e.getGroupId(),
                        nameOfGroup(e.getGroupId()), e.getStatus(), e.getStartedAt(), e.getFinishedAt(),
                        e.getElapsedMs(), e.getErrorMessage())));
        workflowExecutions.selectList(Wrappers.<WorkflowExecutionEntity>lambdaQuery()
                        .orderByDesc(WorkflowExecutionEntity::getStartedAt).last("LIMIT " + capped))
                .forEach(e -> result.add(new ExecutionSummary(e.getId(), "WORKFLOW", e.getWorkflowId(),
                        nameOfWorkflow(e.getWorkflowId()), e.getStatus(), e.getStartedAt(), e.getFinishedAt(),
                        e.getElapsedMs(), e.getErrorMessage())));
        return result.stream().sorted(Comparator.comparing(ExecutionSummary::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(capped).toList();
    }

    @Override
    public List<StepExecutionDetail> steps(String workflowExecutionId) {
        List<StepExecutionEntity> all = new ArrayList<>(stepExecutions.selectList(
                Wrappers.<StepExecutionEntity>lambdaQuery().eq(StepExecutionEntity::getExecutionId, workflowExecutionId)));
        List<String> hookIds = hookExecutions.selectList(Wrappers.<HookExecutionEntity>lambdaQuery()
                        .eq(HookExecutionEntity::getWorkflowExecutionId, workflowExecutionId))
                .stream().map(HookExecutionEntity::getId).toList();

        // The supplied id may also be a group execution id. In that case include the group hook,
        // all child workflow hooks and all normal workflow steps in one chronological report.
        List<String> workflowIds = workflowExecutions.selectList(Wrappers.<WorkflowExecutionEntity>lambdaQuery()
                        .eq(WorkflowExecutionEntity::getGroupExecutionId, workflowExecutionId))
                .stream().map(WorkflowExecutionEntity::getId).toList();
        if (!workflowIds.isEmpty()) {
            all.addAll(stepExecutions.selectList(Wrappers.<StepExecutionEntity>lambdaQuery()
                    .in(StepExecutionEntity::getExecutionId, workflowIds)));
            hookIds = new ArrayList<>(hookExecutions.selectList(Wrappers.<HookExecutionEntity>lambdaQuery()
                            .eq(HookExecutionEntity::getGroupExecutionId, workflowExecutionId))
                    .stream().map(HookExecutionEntity::getId).toList());
        }
        if (!hookIds.isEmpty()) all.addAll(stepExecutions.selectList(Wrappers.<StepExecutionEntity>lambdaQuery()
                .in(StepExecutionEntity::getHookExecutionId, hookIds)));
        return all.stream().collect(java.util.stream.Collectors.toMap(StepExecutionEntity::getId, e -> e, (a, b) -> a))
                .values().stream().sorted(Comparator.comparing(StepExecutionEntity::getStartedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> new StepExecutionDetail(e.getId(), e.getStepCode(), e.getPhase(),
                        e.getStatus(), e.getRequestJson(), e.getResponseJson(), e.getOutputJson(),
                        e.getExtractedJson(), e.getAssertionJson(), e.getElapsedMs(), e.getErrorMessage()))
                .toList();
    }

    private String nameOfGroup(String id) { var e = groups.selectById(id); return e == null ? id : e.getName(); }
    private String nameOfWorkflow(String id) { var e = workflows.selectById(id); return e == null ? id : e.getName(); }
}

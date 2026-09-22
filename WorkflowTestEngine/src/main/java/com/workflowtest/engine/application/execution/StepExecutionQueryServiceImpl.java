package com.workflowtest.engine.application.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.execution.ExecutionModels.StepExecutionDetail;
import com.workflowtest.engine.api.execution.StepExecutionQueryService;
import com.workflowtest.engine.persistence.entity.HookExecutionEntity;
import com.workflowtest.engine.persistence.entity.StepExecutionEntity;
import com.workflowtest.engine.persistence.entity.WorkflowExecutionEntity;
import com.workflowtest.engine.persistence.mapper.HookExecutionMapper;
import com.workflowtest.engine.persistence.mapper.StepExecutionMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StepExecutionQueryServiceImpl implements StepExecutionQueryService {
    private final StepExecutionMapper stepExecutions;
    private final HookExecutionMapper hookExecutions;
    private final WorkflowExecutionMapper workflowExecutions;

    @Override
    public List<StepExecutionDetail> listByExecution(String workflowExecutionId) {
        Long executionKey = Long.parseLong(workflowExecutionId);
        List<StepExecutionEntity> all = new ArrayList<>(stepExecutions.selectList(
                Wrappers.<StepExecutionEntity>lambdaQuery().eq(StepExecutionEntity::getExecutionId, executionKey)));
        List<Long> hookIds = hookExecutions.selectList(Wrappers.<HookExecutionEntity>lambdaQuery()
                        .eq(HookExecutionEntity::getWorkflowExecutionId, executionKey))
                .stream().map(HookExecutionEntity::getId).toList();

        List<Long> workflowIds = workflowExecutions.selectList(Wrappers.<WorkflowExecutionEntity>lambdaQuery()
                        .eq(WorkflowExecutionEntity::getGroupExecutionId, executionKey))
                .stream().map(WorkflowExecutionEntity::getId).toList();
        if (!workflowIds.isEmpty()) {
            all.addAll(stepExecutions.selectList(Wrappers.<StepExecutionEntity>lambdaQuery()
                    .in(StepExecutionEntity::getExecutionId, workflowIds)));
            hookIds = new ArrayList<>(hookExecutions.selectList(Wrappers.<HookExecutionEntity>lambdaQuery()
                            .eq(HookExecutionEntity::getGroupExecutionId, executionKey))
                    .stream().map(HookExecutionEntity::getId).toList());
        }
        if (!hookIds.isEmpty()) {
            all.addAll(stepExecutions.selectList(Wrappers.<StepExecutionEntity>lambdaQuery()
                    .in(StepExecutionEntity::getHookExecutionId, hookIds)));
        }
        return all.stream().collect(java.util.stream.Collectors.toMap(StepExecutionEntity::getId, e -> e, (a, b) -> a))
                .values().stream().sorted(Comparator.comparing(StepExecutionEntity::getStartedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> new StepExecutionDetail(String.valueOf(e.getId()), e.getStepCode(), e.getPhase(),
                        e.getStatus(), e.getRequestJson(), e.getResponseJson(), e.getOutputJson(),
                        e.getExtractedJson(), e.getAssertionJson(), e.getElapsedMs(), e.getErrorMessage()))
                .toList();
    }
}

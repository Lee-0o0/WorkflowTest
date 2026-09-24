package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.Step;
import com.workflowtest.server.service.definition.StepDefinitionService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.HookStepEntity;
import com.workflowtest.server.persistence.entity.StepEntity;
import com.workflowtest.server.persistence.mapper.HookMapper;
import com.workflowtest.server.persistence.mapper.HookStepMapper;
import com.workflowtest.server.persistence.mapper.StepMapper;
import com.workflowtest.server.persistence.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StepDefinitionServiceImpl implements StepDefinitionService {
    private final StepMapper stepMapper;
    private final HookStepMapper hookStepMapper;
    private final HookMapper hookMapper;
    private final WorkflowMapper workflowMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    public Step saveWorkflowStep(Step step) {
        support.validateStep(step);
        support.require(workflowMapper.selectById(step.ownerId()), "工作流不存在");
        StepEntity entity = support.blank(step.id()) ? new StepEntity()
                : support.require(stepMapper.selectById(step.id()), "步骤不存在");
        support.copyStep(step, entity);
        entity.setWorkflowId(step.ownerId());
        support.persist(stepMapper, entity, step.id());
        return support.toStep(entity);
    }

    @Override
    public Step saveHookStep(Long hookId, Step step) {
        support.validateStep(step);
        support.require(hookMapper.selectById(hookId), "钩子不存在");
        HookStepEntity entity = support.blank(step.id()) ? new HookStepEntity()
                : support.require(hookStepMapper.selectById(step.id()), "钩子步骤不存在");
        support.copyStep(step, entity);
        entity.setHookId(hookId);
        support.persist(hookStepMapper, entity, step.id());
        return support.toHookStep(entity);
    }

    @Override
    public void move(Long id, boolean hookStep, int delta) {
        if (hookStep) {
            HookStepEntity current = support.require(hookStepMapper.selectById(id), "钩子步骤不存在");
            List<HookStepEntity> items = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                    .eq(HookStepEntity::getHookId, current.getHookId()).orderByAsc(HookStepEntity::getSortOrder));
            support.reorder(items, id, delta, HookStepEntity::getId, (item, order) -> {
                item.setSortOrder(order);
                hookStepMapper.updateById(item);
            });
        } else {
            StepEntity current = support.require(stepMapper.selectById(id), "步骤不存在");
            List<StepEntity> items = stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                    .eq(StepEntity::getWorkflowId, current.getWorkflowId()).orderByAsc(StepEntity::getSortOrder));
            support.reorder(items, id, delta, StepEntity::getId, (item, order) -> {
                item.setSortOrder(order);
                stepMapper.updateById(item);
            });
        }
    }

    @Override
    public void delete(Long id, boolean hookStep) {
        if (hookStep) hookStepMapper.deleteById(id);
        else stepMapper.deleteById(id);
    }
}

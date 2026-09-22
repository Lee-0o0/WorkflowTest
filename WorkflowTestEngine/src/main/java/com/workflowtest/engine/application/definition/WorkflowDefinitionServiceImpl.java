package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.DefinitionModels.Workflow;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.api.definition.WorkflowDefinitionService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.StepEntity;
import com.workflowtest.engine.persistence.entity.WorkflowEntity;
import com.workflowtest.engine.persistence.mapper.StepMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowGroupMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl implements WorkflowDefinitionService {
    private final WorkflowMapper workflowMapper;
    private final WorkflowGroupMapper groupMapper;
    private final StepMapper stepMapper;
    private final ScopedVariableService scopedVariableService;
    private final DefinitionPersistenceSupport support;

    @Override
    public Workflow save(Long id, Long groupId, String name, String description, int sortOrder) {
        support.require(groupMapper.selectById(groupId), "组不存在");
        support.requireName(name);
        WorkflowEntity entity = support.blank(id) ? new WorkflowEntity()
                : support.require(workflowMapper.selectById(id), "工作流不存在");
        entity.setGroupId(groupId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        support.persist(workflowMapper, entity, id);
        return support.toWorkflow(entity);
    }

    @Override
    public void move(Long id, int delta) {
        WorkflowEntity current = support.require(workflowMapper.selectById(id), "工作流不存在");
        List<WorkflowEntity> items = workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                .eq(WorkflowEntity::getGroupId, current.getGroupId())
                .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName));
        support.reorder(items, id, delta, WorkflowEntity::getId, (item, order) -> {
            item.setSortOrder(order);
            workflowMapper.updateById(item);
        });
    }

    @Override
    public void delete(Long id) {
        stepMapper.delete(Wrappers.<StepEntity>lambdaQuery().eq(StepEntity::getWorkflowId, id));
        scopedVariableService.deleteByScope(ScopeType.WORKFLOW, id);
        workflowMapper.deleteById(id);
    }
}

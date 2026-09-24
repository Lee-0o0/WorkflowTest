package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.ScopeType;
import com.workflowtest.server.service.definition.DefinitionModels.Workflow;
import com.workflowtest.server.service.definition.ScopedVariableService;
import com.workflowtest.server.service.definition.WorkflowDefinitionService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.StepEntity;
import com.workflowtest.server.persistence.entity.WorkflowEntity;
import com.workflowtest.server.persistence.mapper.StepMapper;
import com.workflowtest.server.persistence.mapper.WorkflowGroupMapper;
import com.workflowtest.server.persistence.mapper.WorkflowMapper;
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

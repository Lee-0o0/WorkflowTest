package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.Group;
import com.workflowtest.engine.api.definition.DefinitionModels.HookType;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.HookDefinitionService;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.api.definition.WorkflowDefinitionService;
import com.workflowtest.engine.api.definition.WorkflowGroupService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.WorkflowEntity;
import com.workflowtest.engine.persistence.entity.WorkflowGroupEntity;
import com.workflowtest.engine.persistence.mapper.ProjectMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowGroupMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowGroupServiceImpl implements WorkflowGroupService {
    private final WorkflowGroupMapper groupMapper;
    private final WorkflowMapper workflowMapper;
    private final ProjectMapper projectMapper;
    private final WorkflowDefinitionService workflowDefinitionService;
    private final HookDefinitionService hookDefinitionService;
    private final ScopedVariableService scopedVariableService;
    private final DefinitionPersistenceSupport support;

    @Override
    public Group save(Long id, Long projectId, String name, String description, int sortOrder) {
        support.require(projectMapper.selectById(projectId), "项目不存在");
        support.requireName(name);
        WorkflowGroupEntity entity = support.blank(id) ? new WorkflowGroupEntity()
                : support.require(groupMapper.selectById(id), "组不存在");
        entity.setProjectId(projectId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        boolean creating = support.blank(id);
        support.persist(groupMapper, entity, id);
        if (creating) {
            hookDefinitionService.create(entity.getId(), HookType.BEFORE_GROUP);
            hookDefinitionService.create(entity.getId(), HookType.AFTER_GROUP);
        }
        return support.toGroup(entity);
    }

    @Override
    public void move(Long id, int delta) {
        WorkflowGroupEntity current = support.require(groupMapper.selectById(id), "组不存在");
        List<WorkflowGroupEntity> items = groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                .eq(WorkflowGroupEntity::getProjectId, current.getProjectId())
                .orderByAsc(WorkflowGroupEntity::getSortOrder, WorkflowGroupEntity::getName));
        support.reorder(items, id, delta, WorkflowGroupEntity::getId, (item, order) -> {
            item.setSortOrder(order);
            groupMapper.updateById(item);
        });
    }

    @Override
    public void delete(Long id) {
        workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery().eq(WorkflowEntity::getGroupId, id))
                .forEach(workflow -> workflowDefinitionService.delete(workflow.getId()));
        hookDefinitionService.deleteByGroup(id);
        scopedVariableService.deleteByScope(ScopeType.GROUP, id);
        groupMapper.deleteById(id);
    }
}

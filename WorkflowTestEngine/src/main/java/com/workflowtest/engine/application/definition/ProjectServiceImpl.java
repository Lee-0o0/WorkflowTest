package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.DefinitionModels.Project;
import com.workflowtest.engine.api.definition.ProjectResourceService;
import com.workflowtest.engine.api.definition.ProjectService;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.api.definition.WorkflowGroupService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.ProjectEntity;
import com.workflowtest.engine.persistence.entity.WorkflowGroupEntity;
import com.workflowtest.engine.persistence.mapper.ProjectMapper;
import com.workflowtest.engine.persistence.mapper.WorkflowGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private final ProjectMapper projectMapper;
    private final WorkflowGroupMapper groupMapper;
    private final WorkflowGroupService workflowGroupService;
    private final ScopedVariableService scopedVariableService;
    private final ProjectResourceService projectResourceService;
    private final DefinitionPersistenceSupport support;

    @Override
    public Project save(Long id, String name, String description) {
        support.requireName(name);
        ProjectEntity entity = support.blank(id) ? new ProjectEntity()
                : support.require(projectMapper.selectById(id), "项目不存在");
        entity.setName(name.trim());
        entity.setDescription(description);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        support.persist(projectMapper, entity, id);
        return support.toProject(entity);
    }

    @Override
    public void delete(Long id) {
        groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                        .eq(WorkflowGroupEntity::getProjectId, id))
                .forEach(group -> workflowGroupService.delete(group.getId()));
        scopedVariableService.deleteByScope(ScopeType.PROJECT, id);
        projectResourceService.deleteByProject(id);
        projectMapper.deleteById(id);
    }
}

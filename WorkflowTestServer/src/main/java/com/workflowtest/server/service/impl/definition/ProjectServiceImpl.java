package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.ScopeType;
import com.workflowtest.server.service.definition.DefinitionModels.Project;
import com.workflowtest.server.service.definition.ProjectResourceService;
import com.workflowtest.server.service.definition.ProjectHookDefinitionService;
import com.workflowtest.server.service.definition.ProjectService;
import com.workflowtest.server.service.definition.ScopedVariableService;
import com.workflowtest.server.service.definition.WorkflowGroupService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.ProjectEntity;
import com.workflowtest.server.persistence.entity.WorkflowGroupEntity;
import com.workflowtest.server.persistence.mapper.ProjectMapper;
import com.workflowtest.server.persistence.mapper.WorkflowGroupMapper;
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
    private final ProjectHookDefinitionService projectHookDefinitionService;
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
        projectHookDefinitionService.deleteByProject(id);
        projectMapper.deleteById(id);
    }
}

package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.*;
import com.workflowtest.server.service.definition.ProjectHookDefinitionService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.ProjectHookEntity;
import com.workflowtest.server.persistence.entity.ProjectHookStepEntity;
import com.workflowtest.server.persistence.mapper.ProjectHookMapper;
import com.workflowtest.server.persistence.mapper.ProjectHookStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectHookDefinitionServiceImpl implements ProjectHookDefinitionService {
    private final ProjectHookMapper projectHookMapper;
    private final ProjectHookStepMapper projectHookStepMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    public Optional<ProjectHook> find(Long projectId, ProjectHookType hookType) {
        ProjectHookEntity entity = projectHookMapper.selectOne(Wrappers.<ProjectHookEntity>lambdaQuery()
                .eq(ProjectHookEntity::getProjectId, projectId)
                .eq(ProjectHookEntity::getHookType, hookType.name()));
        return entity == null ? Optional.empty() : Optional.of(toProjectHook(entity));
    }

    @Override
    public ProjectHook create(Long projectId, ProjectHookType hookType) {
        if (find(projectId, hookType).isPresent()) {
            throw new IllegalStateException("项目钩子已存在: projectId=" + projectId + ", hookType=" + hookType);
        }
        ProjectHookEntity entity = new ProjectHookEntity();
        entity.setProjectId(projectId);
        entity.setHookType(hookType.name());
        entity.setEnabled(true);
        projectHookMapper.insert(entity);
        return toProjectHook(entity);
    }

    @Override
    public List<ProjectHook> listByProject(Long projectId) {
        return projectHookMapper.selectList(Wrappers.<ProjectHookEntity>lambdaQuery()
                        .eq(ProjectHookEntity::getProjectId, projectId)
                        .orderByAsc(ProjectHookEntity::getHookType))
                .stream().map(this::toProjectHook).toList();
    }

    @Override
    public List<Step> listSteps(Long projectHookId) {
        support.require(projectHookMapper.selectById(projectHookId), "项目钩子不存在");
        return projectHookStepMapper.selectList(Wrappers.<ProjectHookStepEntity>lambdaQuery()
                        .eq(ProjectHookStepEntity::getProjectHookId, projectHookId)
                        .orderByAsc(ProjectHookStepEntity::getSortOrder))
                .stream().map(support::toProjectHookStep).toList();
    }

    @Override
    public Step saveStep(Long projectHookId, Step step) {
        support.validateStep(step);
        support.require(projectHookMapper.selectById(projectHookId), "项目钩子不存在");
        ProjectHookStepEntity entity = support.blank(step.id()) ? new ProjectHookStepEntity()
                : support.require(projectHookStepMapper.selectById(step.id()), "项目钩子步骤不存在");
        entity.setProjectHookId(projectHookId);
        support.copyStep(step, entity);
        support.persist(projectHookStepMapper, entity, step.id());
        return support.toProjectHookStep(entity);
    }

    @Override
    public void deleteStep(Long stepId) {
        support.require(projectHookStepMapper.selectById(stepId), "项目钩子步骤不存在");
        projectHookStepMapper.deleteById(stepId);
    }

    @Override
    public void deleteByProject(Long projectId) {
        projectHookMapper.selectList(Wrappers.<ProjectHookEntity>lambdaQuery()
                        .eq(ProjectHookEntity::getProjectId, projectId))
                .forEach(hook -> {
                    projectHookStepMapper.delete(Wrappers.<ProjectHookStepEntity>lambdaQuery()
                            .eq(ProjectHookStepEntity::getProjectHookId, hook.getId()));
                    projectHookMapper.deleteById(hook.getId());
                });
    }

    private ProjectHook toProjectHook(ProjectHookEntity entity) {
        return new ProjectHook(entity.getId(), entity.getProjectId(),
                ProjectHookType.valueOf(entity.getHookType()), Boolean.TRUE.equals(entity.getEnabled()), List.of());
    }
}

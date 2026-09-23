package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.definition.ProjectTreeService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.*;
import com.workflowtest.engine.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectTreeServiceImpl implements ProjectTreeService {
    private final ProjectMapper projectMapper;
    private final WorkflowGroupMapper groupMapper;
    private final WorkflowMapper workflowMapper;
    private final StepMapper stepMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    public List<Project> listProjects() {
        return projectMapper.selectList(Wrappers.<ProjectEntity>lambdaQuery().orderByAsc(ProjectEntity::getName))
                .stream().map(support::toProject).toList();
    }

    @Override
    public List<Group> listGroups(Long projectId) {
        return groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                        .eq(WorkflowGroupEntity::getProjectId, projectId)
                        .orderByAsc(WorkflowGroupEntity::getSortOrder, WorkflowGroupEntity::getName))
                .stream().map(support::toGroup).toList();
    }

    @Override
    public List<Workflow> listWorkflows(Long groupId) {
        return workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                        .eq(WorkflowEntity::getGroupId, groupId)
                        .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName))
                .stream().map(support::toWorkflow).toList();
    }

    @Override
    public List<Step> listWorkflowSteps(Long workflowId) {
        return stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                        .eq(StepEntity::getWorkflowId, workflowId)
                        .orderByAsc(StepEntity::getSortOrder))
                .stream().map(support::toStep).toList();
    }
}

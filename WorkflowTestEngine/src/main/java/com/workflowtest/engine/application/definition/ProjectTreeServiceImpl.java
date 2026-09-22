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
    public ProjectTree loadTree() {
        List<ProjectNode> projects = projectMapper.selectList(
                        Wrappers.<ProjectEntity>lambdaQuery().orderByAsc(ProjectEntity::getName))
                .stream().map(project -> new ProjectNode(support.toProject(project), groups(project.getId()))).toList();
        return new ProjectTree(projects);
    }

    private List<GroupNode> groups(Long projectId) {
        return groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                        .eq(WorkflowGroupEntity::getProjectId, projectId)
                        .orderByAsc(WorkflowGroupEntity::getSortOrder, WorkflowGroupEntity::getName))
                .stream().map(group -> new GroupNode(support.toGroup(group), workflows(group.getId()))).toList();
    }

    private List<WorkflowNode> workflows(Long groupId) {
        return workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                        .eq(WorkflowEntity::getGroupId, groupId)
                        .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName))
                .stream().map(workflow -> new WorkflowNode(support.toWorkflow(workflow), workflowSteps(workflow.getId())))
                .toList();
    }

    private List<Step> workflowSteps(Long workflowId) {
        return stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                        .eq(StepEntity::getWorkflowId, workflowId)
                        .orderByAsc(StepEntity::getSortOrder))
                .stream().map(support::toStep).toList();
    }
}

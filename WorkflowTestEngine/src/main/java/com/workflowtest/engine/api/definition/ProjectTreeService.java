package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Group;
import com.workflowtest.engine.api.definition.DefinitionModels.Project;
import com.workflowtest.engine.api.definition.DefinitionModels.Step;
import com.workflowtest.engine.api.definition.DefinitionModels.Workflow;

import java.util.List;

/**
 * 项目树分层查询接口
 */
public interface ProjectTreeService {
    /**
     * 查询全部项目（第一层）
     * @return 项目列表
     */
    List<Project> listProjects();

    /**
     * 查询项目下的组（第二层）
     * @param projectId 项目主键
     * @return 组列表
     */
    List<Group> listGroups(Long projectId);

    /**
     * 查询组下的工作流（第三层）
     * @param groupId 组主键
     * @return 工作流列表
     */
    List<Workflow> listWorkflows(Long groupId);

    /**
     * 查询工作流下的步骤（第四层）
     * @param workflowId 工作流主键
     * @return 步骤列表
     */
    List<Step> listWorkflowSteps(Long workflowId);
}

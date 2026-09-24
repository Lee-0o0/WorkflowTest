package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.ProjectHook;
import com.workflowtest.server.service.definition.DefinitionModels.ProjectHookType;
import com.workflowtest.server.service.definition.DefinitionModels.Step;

import java.util.List;
import java.util.Optional;

public interface ProjectHookDefinitionService {
    Optional<ProjectHook> find(Long projectId, ProjectHookType hookType);
    ProjectHook create(Long projectId, ProjectHookType hookType);
    List<ProjectHook> listByProject(Long projectId);
    List<Step> listSteps(Long projectHookId);
    Step saveStep(Long projectHookId, Step step);
    void deleteStep(Long stepId);
    void deleteByProject(Long projectId);
}

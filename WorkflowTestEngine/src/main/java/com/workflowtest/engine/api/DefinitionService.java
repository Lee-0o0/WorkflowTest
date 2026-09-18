package com.workflowtest.engine.api;

import com.workflowtest.engine.api.DefinitionModels.*;

import java.util.List;

public interface DefinitionService {
    ProjectTree loadTree();

    Project saveProject(String id, String name, String description);
    Group saveGroup(String id, String projectId, String name, String description, int sortOrder);
    Workflow saveWorkflow(String id, String groupId, String name, String description, int sortOrder);
    Step saveWorkflowStep(Step step);
    void moveGroup(String id, int delta);
    void moveWorkflow(String id, int delta);
    void moveStep(String id, boolean hookStep, int delta);
    void deleteProject(String id);
    void deleteGroup(String id);
    void deleteWorkflow(String id);
    void deleteStep(String id, boolean hookStep);

    List<ScopedVariable> listVariables(ScopeType type, String scopeId);
    ScopedVariable saveVariable(ScopedVariable variable);
    void deleteVariable(String id);
    EffectiveEnvironment previewEnvironment(String projectId, String groupId, String workflowId);

    Hook getOrCreateHook(OwnerType ownerType, String ownerId, HookType hookType);
    List<Hook> listHooks(OwnerType ownerType, String ownerId);
    Step saveHookStep(String hookId, Step step);

    List<RuntimeDataSource> listDataSources(String projectId);
    RuntimeDataSource saveDataSource(RuntimeDataSource source, String password);
    boolean testDataSource(String id);
    void deleteDataSource(String id);
}

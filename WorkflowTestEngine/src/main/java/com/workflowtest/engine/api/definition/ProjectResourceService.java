package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResource;
import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResourceType;

import java.util.List;
import java.util.Map;

public interface ProjectResourceService {
    List<ProjectResource> list(Long projectId);

    List<ProjectResource> list(Long projectId, ProjectResourceType type);

    ProjectResource save(ProjectResource resource, String secret);

    boolean testConnection(Long id);

    void testDatasourceConnection(Map<String, Object> config, String username, String secret, Long existingResourceId);

    void delete(Long id);

    void deleteByProject(Long projectId);
}

package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Group;

public interface WorkflowGroupService {
    Group save(Long id, Long projectId, String name, String description, int sortOrder);

    void move(Long id, int delta);

    void delete(Long id);
}

package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Workflow;

public interface WorkflowDefinitionService {
    Workflow save(Long id, Long groupId, String name, String description, int sortOrder);

    void move(Long id, int delta);

    void delete(Long id);
}

package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Project;

public interface ProjectService {
    Project save(Long id, String name, String description);

    void delete(Long id);
}

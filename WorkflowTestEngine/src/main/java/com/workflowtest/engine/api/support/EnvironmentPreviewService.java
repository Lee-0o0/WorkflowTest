package com.workflowtest.engine.api.support;

import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;

public interface EnvironmentPreviewService {
    EffectiveEnvironment preview(Long projectId, Long groupId, Long workflowId);
}

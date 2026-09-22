package com.workflowtest.engine.application.support;

import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;
import com.workflowtest.engine.api.support.EnvironmentPreviewService;
import com.workflowtest.engine.runtime.EnvironmentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvironmentPreviewServiceImpl implements EnvironmentPreviewService {
    private final EnvironmentResolver environmentResolver;

    @Override
    public EffectiveEnvironment preview(Long projectId, Long groupId, Long workflowId) {
        return environmentResolver.resolve(projectId, groupId, workflowId);
    }
}

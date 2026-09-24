package com.workflowtest.server.service.impl.support;

import com.workflowtest.server.service.definition.DefinitionModels.EffectiveEnvironment;
import com.workflowtest.server.service.support.EnvironmentPreviewService;
import com.workflowtest.server.runtime.EnvironmentResolver;
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

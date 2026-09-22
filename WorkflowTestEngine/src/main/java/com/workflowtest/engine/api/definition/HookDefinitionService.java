package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Hook;
import com.workflowtest.engine.api.definition.DefinitionModels.HookType;

import java.util.List;
import java.util.Optional;

public interface HookDefinitionService {
    Optional<Hook> find(Long groupId, HookType hookType);

    Hook create(Long groupId, HookType hookType);

    List<Hook> listByGroup(Long groupId);

    void deleteByGroup(Long groupId);
}

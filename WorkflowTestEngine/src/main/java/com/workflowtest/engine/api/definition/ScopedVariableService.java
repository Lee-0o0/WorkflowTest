package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopedVariable;

import java.util.List;

public interface ScopedVariableService {
    List<ScopedVariable> list(ScopeType type, Long scopeId);

    ScopedVariable save(ScopedVariable variable);

    void delete(Long id);

    void deleteByScope(ScopeType type, Long scopeId);
}

package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.GlobalVariable;

import java.util.List;

public interface GlobalVariableService {
    List<GlobalVariable> list();

    GlobalVariable save(GlobalVariable variable);

    void delete(Long id);
}

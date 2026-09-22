package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Step;

public interface StepDefinitionService {
    Step saveWorkflowStep(Step step);

    Step saveHookStep(Long hookId, Step step);

    void move(Long id, boolean hookStep, int delta);

    void delete(Long id, boolean hookStep);
}

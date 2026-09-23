package com.workflowtest.engine.api.definition;

import java.util.List;
import java.util.Map;

public final class DefinitionModels {
    private DefinitionModels() {}

    public enum ScopeType { PROJECT, GROUP, WORKFLOW }
    public enum HookType { BEFORE_GROUP, AFTER_GROUP }
    public enum StepType { HTTP, SQL, DELAY }
    public enum ProjectResourceType { DATASOURCE, FILE }

    public record Project(Long id, String name, String description, boolean enabled) {}
    public record Group(Long id, Long projectId, String name, String description,
                        int sortOrder, boolean enabled) {}
    public record Workflow(Long id, Long groupId, String name, String description,
                           int sortOrder, boolean enabled) {}
    public record Step(Long id, Long ownerId, String code, String name, StepType type,
                       int sortOrder, boolean enabled, String configJson,
                       String extractionJson, String assertionJson, boolean hookStep) {}
    public record ScopedVariable(Long id, ScopeType scopeType, Long scopeId, String key,
                                 String valueType, Object value, boolean enabled) {}
    public record GlobalVariable(Long id, String key, String valueType, Object value, boolean enabled) {}
    public record Hook(Long id, Long groupId, HookType hookType,
                       boolean enabled, List<Step> steps) {}
    public record ProjectResource(Long id, Long projectId, ProjectResourceType type, String name,
                                  Map<String, Object> config, boolean enabled) {}
    public record EffectiveEnvironment(Map<String, Object> global,
                                       Map<String, Object> project,
                                       Map<String, Object> group,
                                       Map<String, Object> workflow,
                                       Map<String, Object> effective,
                                       Map<String, String> sources) {}
}

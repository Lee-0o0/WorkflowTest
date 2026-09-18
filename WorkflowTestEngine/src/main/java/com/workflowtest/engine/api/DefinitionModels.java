package com.workflowtest.engine.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

public final class DefinitionModels {
    private DefinitionModels() {}

    public enum ScopeType { PROJECT, GROUP, WORKFLOW }
    public enum HookType { BEFORE_GROUP, BEFORE_WORKFLOW }
    public enum OwnerType { GROUP, WORKFLOW }
    public enum StepType { HTTP, SQL, DELAY }
    public enum FailureStrategy { STOP, CONTINUE, IGNORE }

    public record Project(String id, String name, String description, boolean enabled) {}
    public record Group(String id, String projectId, String name, String description,
                        int sortOrder, boolean enabled) {}
    public record Workflow(String id, String groupId, String name, String description,
                           int sortOrder, boolean enabled) {}
    public record Step(String id, String ownerId, String code, String name, StepType type,
                       int sortOrder, boolean enabled, String configJson,
                       String extractionJson, String assertionJson,
                       FailureStrategy failureStrategy, String retryJson, boolean hookStep) {}
    public record ScopedVariable(String id, ScopeType scopeType, String scopeId, String key,
                                 String valueType, Object value, boolean sensitive, boolean enabled) {}
    public record Hook(String id, OwnerType ownerType, String ownerId, HookType hookType,
                       boolean enabled, FailureStrategy failureStrategy, List<Step> steps) {}
    public record RuntimeDataSource(String id, String projectId, String name, String driverClass,
                                    String jdbcUrl, String username, boolean allowDangerousSql,
                                    boolean enabled) {}
    public record ProjectTree(List<ProjectNode> projects) {}
    public record ProjectNode(Project project, List<GroupNode> groups) {}
    public record GroupNode(Group group, List<WorkflowNode> workflows) {}
    public record WorkflowNode(Workflow workflow, List<Step> steps) {}
    public record EffectiveEnvironment(Map<String, Object> global,
                                       Map<String, Object> project,
                                       Map<String, Object> group,
                                       Map<String, Object> workflow,
                                       Map<String, Object> effective,
                                       Map<String, String> sources,
                                       Set<String> sensitiveKeys) {
        public EffectiveEnvironment redacted() {
            return new EffectiveEnvironment(mask(global), mask(project), mask(group), mask(workflow),
                    mask(effective), sources, sensitiveKeys);
        }
        private Map<String, Object> mask(Map<String, Object> values) {
            Map<String, Object> result = new LinkedHashMap<>(values);
            sensitiveKeys.forEach(key -> { if (result.containsKey(key)) result.put(key, "******"); });
            return Map.copyOf(result);
        }
    }
}

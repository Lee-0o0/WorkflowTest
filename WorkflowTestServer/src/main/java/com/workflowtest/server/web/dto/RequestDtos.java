package com.workflowtest.server.web.dto;

import com.workflowtest.server.service.definition.DefinitionModels.ScopeType;
import com.workflowtest.server.service.definition.DefinitionModels.StepType;

import java.util.Map;

public final class RequestDtos {
    private RequestDtos() {}

    public record SaveProjectRequest(String name, String description) {}

    public record SaveGroupRequest(Long projectId, String name, String description, Integer sortOrder) {}

    public record SaveWorkflowRequest(Long groupId, String name, String description, Integer sortOrder) {}

    public record SaveStepRequest(
            Long ownerId,
            String code,
            String name,
            StepType type,
            Integer sortOrder,
            Boolean enabled,
            String configJson,
            String extractionJson,
            String assertionJson,
            Boolean hookStep) {}

    public record SaveHookStepRequest(
            Long id,
            Long hookId,
            String code,
            String name,
            StepType type,
            Integer sortOrder,
            Boolean enabled,
            String configJson,
            String extractionJson,
            String assertionJson) {}

    public record SaveScopedVariableRequest(
            ScopeType scopeType,
            Long scopeId,
            String key,
            String valueType,
            Object value,
            Boolean enabled) {}

    public record SaveGlobalVariableRequest(String key, String valueType, Object value, Boolean enabled) {}

    public record SaveProjectResourceRequest(
            Long projectId,
            String type,
            String name,
            Map<String, Object> config,
            Boolean enabled,
            String secret) {}

    public record TestDatasourceRequest(
            Map<String, Object> config,
            String username,
            String secret,
            Long existingResourceId) {}

    public record MoveRequest(int delta) {}

    public record BackupRequest(String destinationDirectory) {}
}

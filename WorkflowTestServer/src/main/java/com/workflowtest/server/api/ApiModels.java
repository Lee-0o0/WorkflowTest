package com.workflowtest.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflowtest.server.domain.Roles.ProjectRole;
import com.workflowtest.server.domain.Roles.SystemRole;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public final class ApiModels {
    private ApiModels() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, UserView user) {}
    public record UserView(String id, String username, String displayName, SystemRole systemRole, boolean enabled) {}
    public record SaveUserRequest(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,49}") String username,
                                  @NotBlank String displayName, String password,
                                  @NotNull SystemRole systemRole, boolean enabled) {}
    public record ProjectView(String id, String projectKey, String name, String description,
                              boolean enabled, ProjectRole currentRole) {}
    public record SaveProjectRequest(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,29}") String projectKey,
                                     @NotBlank String name, String description) {}
    public record SaveMemberRequest(@NotBlank String userId, @NotNull ProjectRole role) {}
    public record MemberView(String userId, String username, String displayName, ProjectRole role) {}
    public record GroupView(String id, String projectId, String name, String description, int sortOrder, boolean enabled) {}
    public record SaveGroupRequest(@NotBlank String name, String description, @Min(0) int sortOrder) {}
    public record WorkflowView(String id, String groupId, String name, String description, int sortOrder,
                               int revision, JsonNode draft, boolean enabled) {}
    public record SaveWorkflowRequest(@NotBlank String name, String description, @Min(0) int sortOrder, JsonNode draft) {}
    public record UpdateDraftRequest(@Min(0) int expectedRevision, @NotNull JsonNode draft) {}
    public record VersionView(String id, String workflowId, int version, int revision,
                              String checksum, String publishedBy, LocalDateTime publishedAt) {}
    public record ExecutionPackage(String packageVersion, String projectId, String groupId, String workflowId,
                                   String workflowVersionId, int version, String checksum, JsonNode definition) {}
    public record VariableView(String id, String scopeType, String scopeId, String key, JsonNode value, boolean enabled) {}
    public record SaveVariableRequest(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,99}") String key,
                                      @NotNull JsonNode value, boolean enabled) {}
}

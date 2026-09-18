package com.workflowtest.server.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.server.application.VariableService.Scope;
import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.domain.Roles.ProjectRole;
import com.workflowtest.server.persistence.*;
import com.workflowtest.server.persistence.entity.*;
import com.workflowtest.server.security.SecuritySupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class AssetService {
    private static final ProjectRole[] READ = ProjectRole.values();
    private static final ProjectRole[] WRITE = {ProjectRole.PROJECT_ADMIN, ProjectRole.TEST_DEVELOPER};
    private final GroupMapper groups; private final WorkflowMapper workflows; private final WorkflowVersionMapper versions;
    private final ProjectAccessService access; private final ObjectMapper json;
    private final VariableService variables;

    public AssetService(GroupMapper groups, WorkflowMapper workflows, WorkflowVersionMapper versions,
                        ProjectAccessService access, ObjectMapper json, VariableService variables) {
        this.groups = groups; this.workflows = workflows; this.versions = versions; this.access = access; this.json = json;
        this.variables = variables;
    }

    public List<GroupView> groups(String projectId) {
        access.require(projectId, READ);
        return groups.selectList(Wrappers.<GroupEntity>lambdaQuery().eq(GroupEntity::getProjectId, projectId)
                .orderByAsc(GroupEntity::getSortOrder, GroupEntity::getName)).stream().map(this::groupView).toList();
    }

    @Transactional
    public GroupView saveGroup(String projectId, String id, SaveGroupRequest request) {
        access.require(projectId, WRITE);
        GroupEntity entity = id == null ? new GroupEntity() : require(groups.selectById(id), "组不存在");
        if (id != null && !projectId.equals(entity.getProjectId())) throw new IllegalArgumentException("组不属于指定项目");
        LocalDateTime now = LocalDateTime.now(); entity.setProjectId(projectId); entity.setName(request.name());
        entity.setDescription(request.description()); entity.setSortOrder(request.sortOrder()); entity.setEnabled(true);
        if (id == null) entity.setCreatedAt(now); entity.setUpdatedAt(now);
        if (id == null) groups.insert(entity); else groups.updateById(entity); return groupView(entity);
    }

    public List<WorkflowView> workflows(String groupId) {
        access.require(access.projectOfGroup(groupId), READ);
        return workflows.selectList(Wrappers.<WorkflowEntity>lambdaQuery().eq(WorkflowEntity::getGroupId, groupId)
                .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName)).stream().map(this::workflowView).toList();
    }

    @Transactional
    public void deleteGroup(String projectId, String groupId) {
        access.require(projectId, WRITE);
        GroupEntity group = require(groups.selectById(groupId), "组不存在");
        if (!projectId.equals(group.getProjectId())) throw new IllegalArgumentException("组不属于指定项目");
        workflows.selectList(Wrappers.<WorkflowEntity>lambdaQuery().eq(WorkflowEntity::getGroupId, groupId))
                .forEach(workflow -> variables.deleteScope(Scope.WORKFLOW, workflow.getId()));
        variables.deleteScope(Scope.GROUP, groupId);
        groups.deleteById(groupId);
    }

    @Transactional
    public WorkflowView saveWorkflow(String groupId, String id, SaveWorkflowRequest request) {
        String projectId = access.projectOfGroup(groupId); access.require(projectId, WRITE);
        WorkflowEntity entity = id == null ? new WorkflowEntity() : require(workflows.selectById(id), "工作流不存在");
        if (id != null && !groupId.equals(entity.getGroupId())) throw new IllegalArgumentException("工作流不属于指定组");
        LocalDateTime now = LocalDateTime.now(); entity.setGroupId(groupId); entity.setName(request.name());
        entity.setDescription(request.description()); entity.setSortOrder(request.sortOrder()); entity.setEnabled(true);
        entity.setUpdatedBy(SecuritySupport.current().id());
        if (id == null) { entity.setRevision(0); entity.setCreatedAt(now); }
        if (request.draft() != null) entity.setDraftJson(write(request.draft())); else if (id == null) entity.setDraftJson("{}");
        entity.setUpdatedAt(now);
        if (id == null) workflows.insert(entity); else workflows.updateById(entity); return workflowView(entity);
    }

    @Transactional
    public WorkflowView updateDraft(String workflowId, UpdateDraftRequest request) {
        String projectId = access.projectOfWorkflow(workflowId); access.require(projectId, WRITE);
        int changed = workflows.update(null, Wrappers.<WorkflowEntity>lambdaUpdate()
                .eq(WorkflowEntity::getId, workflowId).eq(WorkflowEntity::getRevision, request.expectedRevision())
                .set(WorkflowEntity::getDraftJson, write(request.draft()))
                .set(WorkflowEntity::getRevision, request.expectedRevision() + 1)
                .set(WorkflowEntity::getUpdatedBy, SecuritySupport.current().id())
                .set(WorkflowEntity::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new ConflictException("工作流已被其他用户修改，请刷新后重试");
        return workflowView(workflows.selectById(workflowId));
    }

    @Transactional
    public void deleteWorkflow(String workflowId) {
        access.require(access.projectOfWorkflow(workflowId), WRITE);
        variables.deleteScope(Scope.WORKFLOW, workflowId);
        workflows.deleteById(workflowId);
    }

    @Transactional
    public VersionView publish(String workflowId) {
        String projectId = access.projectOfWorkflow(workflowId); access.require(projectId, WRITE);
        WorkflowEntity workflow = require(workflows.selectById(workflowId), "工作流不存在");
        GroupEntity group = require(groups.selectById(workflow.getGroupId()), "组不存在");
        WorkflowVersionEntity latest = versions.selectOne(Wrappers.<WorkflowVersionEntity>lambdaQuery()
                .eq(WorkflowVersionEntity::getWorkflowId, workflowId).orderByDesc(WorkflowVersionEntity::getVersionNo).last("LIMIT 1"));
        WorkflowVersionEntity version = new WorkflowVersionEntity(); version.setWorkflowId(workflowId);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1); version.setRevision(workflow.getRevision());
        JsonNode draft = read(workflow.getDraftJson());
        ObjectNode snapshot = draft.isObject() ? (ObjectNode) draft.deepCopy() : json.createObjectNode().set("content", draft);
        ObjectNode environment = snapshot.putObject("environment");
        environment.set("project", variables.values(Scope.PROJECT, group.getProjectId()));
        environment.set("group", variables.values(Scope.GROUP, group.getId()));
        environment.set("workflow", variables.values(Scope.WORKFLOW, workflow.getId()));
        String snapshotJson = write(snapshot);
        version.setSnapshotJson(snapshotJson); version.setChecksum(checksum(snapshotJson));
        version.setPublishedBy(SecuritySupport.current().id()); version.setPublishedAt(LocalDateTime.now()); versions.insert(version);
        return versionView(version);
    }

    public List<VersionView> versions(String workflowId) {
        access.require(access.projectOfWorkflow(workflowId), READ);
        return versions.selectList(Wrappers.<WorkflowVersionEntity>lambdaQuery().eq(WorkflowVersionEntity::getWorkflowId, workflowId)
                .orderByDesc(WorkflowVersionEntity::getVersionNo)).stream().map(this::versionView).toList();
    }

    public ExecutionPackage executionPackage(String versionId) {
        WorkflowVersionEntity version = require(versions.selectById(versionId), "工作流版本不存在");
        WorkflowEntity workflow = require(workflows.selectById(version.getWorkflowId()), "工作流不存在");
        GroupEntity group = require(groups.selectById(workflow.getGroupId()), "组不存在");
        access.require(group.getProjectId(), ProjectRole.PROJECT_ADMIN, ProjectRole.TEST_DEVELOPER, ProjectRole.TEST_EXECUTOR);
        return new ExecutionPackage("1", group.getProjectId(), group.getId(), workflow.getId(), version.getId(),
                version.getVersionNo(), version.getChecksum(), read(version.getSnapshotJson()));
    }

    private GroupView groupView(GroupEntity e) { return new GroupView(e.getId(), e.getProjectId(), e.getName(), e.getDescription(), e.getSortOrder(), Boolean.TRUE.equals(e.getEnabled())); }
    private WorkflowView workflowView(WorkflowEntity e) { return new WorkflowView(e.getId(), e.getGroupId(), e.getName(), e.getDescription(), e.getSortOrder(), e.getRevision(), read(e.getDraftJson()), Boolean.TRUE.equals(e.getEnabled())); }
    private VersionView versionView(WorkflowVersionEntity e) { return new VersionView(e.getId(), e.getWorkflowId(), e.getVersionNo(), e.getRevision(), e.getChecksum(), e.getPublishedBy(), e.getPublishedAt()); }
    private String write(JsonNode value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("配置 JSON 无效", e); } }
    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("数据库中的配置 JSON 无效", e); } }
    private String checksum(String value) { try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T require(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
    public static class ConflictException extends RuntimeException { public ConflictException(String message) { super(message); } }
}

package com.workflowtest.server.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.domain.Roles.ProjectRole;
import com.workflowtest.server.persistence.*;
import com.workflowtest.server.persistence.entity.*;
import com.workflowtest.server.security.SecuritySupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectService {
    private final ProjectMapper projects;
    private final ProjectMemberMapper members;
    private final UserService users;
    private final ProjectAccessService access;
    private final GroupMapper groups;
    private final WorkflowMapper workflows;
    private final ScopeVariableMapper variables;

    public ProjectService(ProjectMapper projects, ProjectMemberMapper members, UserService users, ProjectAccessService access,
                          GroupMapper groups, WorkflowMapper workflows, ScopeVariableMapper variables) {
        this.projects = projects; this.members = members; this.users = users; this.access = access;
        this.groups = groups; this.workflows = workflows; this.variables = variables;
    }

    public List<ProjectView> list() {
        List<ProjectEntity> result;
        if (SecuritySupport.current().admin()) result = projects.selectList(Wrappers.<ProjectEntity>lambdaQuery().orderByAsc(ProjectEntity::getName));
        else {
            List<String> ids = members.selectList(Wrappers.<ProjectMemberEntity>lambdaQuery()
                            .eq(ProjectMemberEntity::getUserId, SecuritySupport.current().id()))
                    .stream().map(ProjectMemberEntity::getProjectId).toList();
            result = ids.isEmpty() ? List.of() : projects.selectByIds(ids);
        }
        return result.stream().map(this::view).toList();
    }

    @Transactional
    public ProjectView create(SaveProjectRequest request) {
        ProjectEntity entity = new ProjectEntity(); LocalDateTime now = LocalDateTime.now();
        entity.setProjectKey(request.projectKey()); entity.setName(request.name()); entity.setDescription(request.description());
        entity.setEnabled(true); entity.setCreatedBy(SecuritySupport.current().id()); entity.setCreatedAt(now); entity.setUpdatedAt(now);
        projects.insert(entity);
        upsertMember(entity.getId(), SecuritySupport.current().id(), ProjectRole.PROJECT_ADMIN);
        return view(entity);
    }

    @Transactional
    public ProjectView update(String id, SaveProjectRequest request) {
        access.require(id, ProjectRole.PROJECT_ADMIN);
        ProjectEntity entity = require(projects.selectById(id), "项目不存在");
        entity.setProjectKey(request.projectKey()); entity.setName(request.name()); entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now()); projects.updateById(entity); return view(entity);
    }

    public List<MemberView> members(String projectId) {
        access.require(projectId, ProjectRole.PROJECT_ADMIN);
        return members.selectList(Wrappers.<ProjectMemberEntity>lambdaQuery().eq(ProjectMemberEntity::getProjectId, projectId))
                .stream().map(member -> {
                    UserEntity user = users.byId(member.getUserId());
                    return new MemberView(user.getId(), user.getUsername(), user.getDisplayName(), ProjectRole.valueOf(member.getProjectRole()));
                }).toList();
    }

    @Transactional
    public MemberView saveMember(String projectId, SaveMemberRequest request) {
        access.require(projectId, ProjectRole.PROJECT_ADMIN);
        UserEntity user = users.byId(request.userId()); upsertMember(projectId, user.getId(), request.role());
        return new MemberView(user.getId(), user.getUsername(), user.getDisplayName(), request.role());
    }

    public void removeMember(String projectId, String userId) {
        access.require(projectId, ProjectRole.PROJECT_ADMIN);
        if (Objects.equals(userId, SecuritySupport.current().id())) throw new IllegalArgumentException("不能移除当前登录用户自己");
        members.delete(Wrappers.<ProjectMemberEntity>lambdaQuery().eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getUserId, userId));
    }

    @Transactional
    public void delete(String projectId) {
        access.require(projectId, ProjectRole.PROJECT_ADMIN);
        List<GroupEntity> projectGroups = groups.selectList(Wrappers.<GroupEntity>lambdaQuery().eq(GroupEntity::getProjectId, projectId));
        List<String> groupIds = projectGroups.stream().map(GroupEntity::getId).toList();
        List<String> workflowIds = groupIds.isEmpty() ? List.of() : workflows.selectList(
                Wrappers.<WorkflowEntity>lambdaQuery().in(WorkflowEntity::getGroupId, groupIds)).stream().map(WorkflowEntity::getId).toList();
        variables.delete(Wrappers.<com.workflowtest.server.persistence.entity.ScopeVariableEntity>lambdaQuery()
                .and(q -> q.eq(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeType, "PROJECT")
                        .eq(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeId, projectId)
                        .or(groupIds.isEmpty() ? false : true, nested -> nested.eq(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeType, "GROUP")
                                .in(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeId, groupIds))
                        .or(workflowIds.isEmpty() ? false : true, nested -> nested.eq(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeType, "WORKFLOW")
                                .in(com.workflowtest.server.persistence.entity.ScopeVariableEntity::getScopeId, workflowIds))));
        projects.deleteById(projectId);
    }

    private void upsertMember(String projectId, String userId, ProjectRole role) {
        ProjectMemberEntity member = members.selectOne(Wrappers.<ProjectMemberEntity>lambdaQuery()
                .eq(ProjectMemberEntity::getProjectId, projectId).eq(ProjectMemberEntity::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        if (member == null) { member = new ProjectMemberEntity(); member.setProjectId(projectId); member.setUserId(userId); member.setCreatedAt(now); }
        member.setProjectRole(role.name()); member.setUpdatedAt(now);
        if (member.getId() == null) members.insert(member); else members.updateById(member);
    }

    private ProjectView view(ProjectEntity e) { return new ProjectView(e.getId(), e.getProjectKey(), e.getName(), e.getDescription(), Boolean.TRUE.equals(e.getEnabled()), access.role(e.getId())); }
    private <T> T require(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
}

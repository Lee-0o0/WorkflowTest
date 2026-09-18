package com.workflowtest.server.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.domain.Roles.ProjectRole;
import com.workflowtest.server.persistence.*;
import com.workflowtest.server.persistence.entity.*;
import com.workflowtest.server.security.SecuritySupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class ProjectAccessService {
    private final ProjectMemberMapper members;
    private final GroupMapper groups;
    private final WorkflowMapper workflows;

    public ProjectAccessService(ProjectMemberMapper members, GroupMapper groups, WorkflowMapper workflows) {
        this.members = members; this.groups = groups; this.workflows = workflows;
    }

    public ProjectRole role(String projectId) {
        if (SecuritySupport.current().admin()) return ProjectRole.PROJECT_ADMIN;
        ProjectMemberEntity member = members.selectOne(Wrappers.<ProjectMemberEntity>lambdaQuery()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getUserId, SecuritySupport.current().id()));
        return member == null ? null : ProjectRole.valueOf(member.getProjectRole());
    }

    public void require(String projectId, ProjectRole... allowed) {
        ProjectRole actual = role(projectId);
        if (actual == null || Arrays.stream(allowed).noneMatch(role -> role == actual))
            throw new AccessDeniedException("没有该项目的操作权限");
    }

    public String projectOfGroup(String groupId) {
        GroupEntity group = groups.selectById(groupId);
        if (group == null) throw new IllegalArgumentException("组不存在");
        return group.getProjectId();
    }

    public String projectOfWorkflow(String workflowId) {
        WorkflowEntity workflow = workflows.selectById(workflowId);
        if (workflow == null) throw new IllegalArgumentException("工作流不存在");
        return projectOfGroup(workflow.getGroupId());
    }
}

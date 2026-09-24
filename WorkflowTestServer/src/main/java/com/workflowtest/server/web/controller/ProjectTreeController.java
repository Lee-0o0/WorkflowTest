package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.ProjectTreeService;
import com.workflowtest.server.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectTreeController {
    private final ProjectTreeService projectTree;

    @GetMapping("/projects")
    public ApiResponse<?> listProjects() {
        return ApiResponse.ok(projectTree.listProjects());
    }

    @GetMapping("/projects/{projectId}/groups")
    public ApiResponse<?> listGroups(@PathVariable Long projectId) {
        return ApiResponse.ok(projectTree.listGroups(projectId));
    }

    @GetMapping("/groups/{groupId}/workflows")
    public ApiResponse<?> listWorkflows(@PathVariable Long groupId) {
        return ApiResponse.ok(projectTree.listWorkflows(groupId));
    }

    @GetMapping("/workflows/{workflowId}/steps")
    public ApiResponse<?> listWorkflowSteps(@PathVariable Long workflowId) {
        return ApiResponse.ok(projectTree.listWorkflowSteps(workflowId));
    }
}

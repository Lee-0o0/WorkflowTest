package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.ProjectHookType;
import com.workflowtest.server.service.definition.ProjectHookDefinitionService;
import com.workflowtest.server.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectHookController {
    private final ProjectHookDefinitionService projectHookDefinitionService;

    @GetMapping("/projects/{projectId}/project-hooks")
    public ApiResponse<?> list(@PathVariable Long projectId) {
        return ApiResponse.ok(projectHookDefinitionService.listByProject(projectId));
    }

    @GetMapping("/projects/{projectId}/project-hooks/{hookType}")
    public ApiResponse<?> find(@PathVariable Long projectId, @PathVariable ProjectHookType hookType) {
        return ApiResponse.ok(projectHookDefinitionService.find(projectId, hookType).orElse(null));
    }

    @PostMapping("/projects/{projectId}/project-hooks/{hookType}")
    public ApiResponse<?> create(@PathVariable Long projectId, @PathVariable ProjectHookType hookType) {
        return ApiResponse.ok(projectHookDefinitionService.create(projectId, hookType));
    }

    @GetMapping("/project-hooks/{projectHookId}/steps")
    public ApiResponse<?> listSteps(@PathVariable Long projectHookId) {
        return ApiResponse.ok(projectHookDefinitionService.listSteps(projectHookId));
    }
}

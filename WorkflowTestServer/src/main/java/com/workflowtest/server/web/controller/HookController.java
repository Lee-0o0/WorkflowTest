package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.HookType;
import com.workflowtest.server.service.definition.HookDefinitionService;
import com.workflowtest.server.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HookController {
    private final HookDefinitionService hookDefinitionService;

    @GetMapping("/groups/{groupId}/hooks")
    public ApiResponse<?> listByGroup(@PathVariable Long groupId) {
        return ApiResponse.ok(hookDefinitionService.listByGroup(groupId));
    }

    @GetMapping("/groups/{groupId}/hooks/{hookType}")
    public ApiResponse<?> find(@PathVariable Long groupId, @PathVariable HookType hookType) {
        return ApiResponse.ok(hookDefinitionService.find(groupId, hookType).orElse(null));
    }

    @PostMapping("/groups/{groupId}/hooks/{hookType}")
    public ApiResponse<?> create(@PathVariable Long groupId, @PathVariable HookType hookType) {
        return ApiResponse.ok(hookDefinitionService.create(groupId, hookType));
    }

    @GetMapping("/hooks/{hookId}/steps")
    public ApiResponse<?> listSteps(@PathVariable Long hookId) {
        return ApiResponse.ok(hookDefinitionService.listSteps(hookId));
    }
}

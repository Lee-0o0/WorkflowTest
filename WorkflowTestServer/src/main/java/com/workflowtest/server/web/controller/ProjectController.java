package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.ProjectService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.SaveProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveProjectRequest request) {
        return ApiResponse.ok(projectService.save(null, request.name(), request.description()));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveProjectRequest request) {
        return ApiResponse.ok(projectService.save(id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }
}

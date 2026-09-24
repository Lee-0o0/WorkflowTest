package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.ProjectResource;
import com.workflowtest.server.service.definition.DefinitionModels.ProjectResourceType;
import com.workflowtest.server.service.definition.ProjectResourceService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.SaveProjectResourceRequest;
import com.workflowtest.server.web.dto.RequestDtos.TestDatasourceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project-resources")
@RequiredArgsConstructor
public class ProjectResourceController {
    private final ProjectResourceService projectResourceService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam Long projectId,
                               @RequestParam(required = false) ProjectResourceType type) {
        if (type == null) {
            return ApiResponse.ok(projectResourceService.list(projectId));
        }
        return ApiResponse.ok(projectResourceService.list(projectId, type));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveProjectResourceRequest request) {
        return ApiResponse.ok(projectResourceService.save(toResource(null, request), request.secret()));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveProjectResourceRequest request) {
        return ApiResponse.ok(projectResourceService.save(toResource(id, request), request.secret()));
    }

    @PostMapping("/{id}/test-connection")
    public ApiResponse<Boolean> testConnection(@PathVariable Long id) {
        return ApiResponse.ok(projectResourceService.testConnection(id));
    }

    @PostMapping("/test-connection")
    public ApiResponse<Void> testDatasource(@RequestBody TestDatasourceRequest request) {
        projectResourceService.testDatasourceConnection(
                request.config(), request.username(), request.secret(), request.existingResourceId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectResourceService.delete(id);
        return ApiResponse.ok();
    }

    private ProjectResource toResource(Long id, SaveProjectResourceRequest request) {
        return new ProjectResource(
                id,
                request.projectId(),
                ProjectResourceType.valueOf(request.type()),
                request.name(),
                request.config(),
                request.enabled() == null || request.enabled());
    }
}

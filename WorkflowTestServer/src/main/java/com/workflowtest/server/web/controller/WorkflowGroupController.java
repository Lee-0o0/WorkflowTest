package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.WorkflowGroupService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.MoveRequest;
import com.workflowtest.server.web.dto.RequestDtos.SaveGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class WorkflowGroupController {
    private final WorkflowGroupService workflowGroupService;

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveGroupRequest request) {
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        return ApiResponse.ok(workflowGroupService.save(null, request.projectId(), request.name(),
                request.description(), sortOrder));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveGroupRequest request) {
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        return ApiResponse.ok(workflowGroupService.save(id, request.projectId(), request.name(),
                request.description(), sortOrder));
    }

    @PostMapping("/{id}/move")
    public ApiResponse<Void> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        workflowGroupService.move(id, request.delta());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workflowGroupService.delete(id);
        return ApiResponse.ok();
    }
}

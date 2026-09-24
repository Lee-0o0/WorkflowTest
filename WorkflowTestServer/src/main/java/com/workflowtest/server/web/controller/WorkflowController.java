package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.WorkflowDefinitionService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.MoveRequest;
import com.workflowtest.server.web.dto.RequestDtos.SaveWorkflowRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowDefinitionService workflowDefinitionService;

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveWorkflowRequest request) {
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        return ApiResponse.ok(workflowDefinitionService.save(null, request.groupId(), request.name(),
                request.description(), sortOrder));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveWorkflowRequest request) {
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        return ApiResponse.ok(workflowDefinitionService.save(id, request.groupId(), request.name(),
                request.description(), sortOrder));
    }

    @PostMapping("/{id}/move")
    public ApiResponse<Void> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        workflowDefinitionService.move(id, request.delta());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workflowDefinitionService.delete(id);
        return ApiResponse.ok();
    }
}

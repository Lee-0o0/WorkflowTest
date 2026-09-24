package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.Step;
import com.workflowtest.server.service.definition.ProjectHookDefinitionService;
import com.workflowtest.server.service.definition.StepDefinitionService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.MoveRequest;
import com.workflowtest.server.web.dto.RequestDtos.SaveHookStepRequest;
import com.workflowtest.server.web.dto.RequestDtos.SaveStepRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class StepController {
    private final StepDefinitionService stepDefinitionService;
    private final ProjectHookDefinitionService projectHookDefinitionService;

    @PostMapping("/api/workflows/{workflowId}/steps")
    public ApiResponse<?> saveWorkflowStep(@PathVariable Long workflowId, @RequestBody SaveStepRequest request) {
        return ApiResponse.ok(stepDefinitionService.saveWorkflowStep(toStep(workflowId, request)));
    }

    @PutMapping("/api/steps/{id}")
    public ApiResponse<?> updateWorkflowStep(@PathVariable Long id, @RequestBody SaveStepRequest request) {
        Long ownerId = request.ownerId() == null ? 0L : request.ownerId();
        return ApiResponse.ok(stepDefinitionService.saveWorkflowStep(toStep(id, ownerId, request)));
    }

    @PostMapping("/api/steps/{id}/move")
    public ApiResponse<Void> moveWorkflowStep(@PathVariable Long id, @RequestBody MoveRequest request) {
        stepDefinitionService.move(id, false, request.delta());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/steps/{id}")
    public ApiResponse<Void> deleteWorkflowStep(@PathVariable Long id) {
        stepDefinitionService.delete(id, false);
        return ApiResponse.ok();
    }

    @PostMapping("/api/hooks/{hookId}/steps")
    public ApiResponse<?> saveHookStep(@PathVariable Long hookId, @RequestBody SaveHookStepRequest request) {
        return ApiResponse.ok(stepDefinitionService.saveHookStep(hookId, toHookStep(hookId, request)));
    }

    @PutMapping("/api/hook-steps/{id}")
    public ApiResponse<?> updateHookStep(@PathVariable Long id, @RequestBody SaveHookStepRequest request) {
        Long hookId = request.hookId() == null ? 0L : request.hookId();
        return ApiResponse.ok(stepDefinitionService.saveHookStep(hookId, toHookStep(id, hookId, request)));
    }

    @PostMapping("/api/hook-steps/{id}/move")
    public ApiResponse<Void> moveHookStep(@PathVariable Long id, @RequestBody MoveRequest request) {
        stepDefinitionService.move(id, true, request.delta());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/hook-steps/{id}")
    public ApiResponse<Void> deleteHookStep(@PathVariable Long id) {
        stepDefinitionService.delete(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/api/project-hooks/{projectHookId}/steps")
    public ApiResponse<?> saveProjectHookStep(@PathVariable Long projectHookId, @RequestBody SaveHookStepRequest request) {
        return ApiResponse.ok(projectHookDefinitionService.saveStep(projectHookId, toProjectHookStep(projectHookId, request)));
    }

    @PutMapping("/api/project-hook-steps/{id}")
    public ApiResponse<?> updateProjectHookStep(@PathVariable Long id, @RequestBody SaveHookStepRequest request) {
        Long projectHookId = request.hookId() == null ? 0L : request.hookId();
        return ApiResponse.ok(projectHookDefinitionService.saveStep(projectHookId, toProjectHookStep(id, projectHookId, request)));
    }

    @DeleteMapping("/api/project-hook-steps/{id}")
    public ApiResponse<Void> deleteProjectHookStep(@PathVariable Long id) {
        projectHookDefinitionService.deleteStep(id);
        return ApiResponse.ok();
    }

    private Step toProjectHookStep(Long projectHookId, SaveHookStepRequest request) {
        return toProjectHookStep(null, projectHookId, request);
    }

    private Step toProjectHookStep(Long id, Long projectHookId, SaveHookStepRequest request) {
        return new Step(
                id,
                projectHookId,
                request.code(),
                request.name(),
                request.type(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.enabled() == null || request.enabled(),
                request.configJson() == null ? "{}" : request.configJson(),
                request.extractionJson(),
                request.assertionJson(),
                true);
    }

    private Step toStep(Long workflowId, SaveStepRequest request) {
        return toStep(null, workflowId, request);
    }

    private Step toStep(Long id, Long ownerId, SaveStepRequest request) {
        return new Step(
                id,
                ownerId,
                request.code(),
                request.name(),
                request.type(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.enabled() == null || request.enabled(),
                request.configJson() == null ? "{}" : request.configJson(),
                request.extractionJson(),
                request.assertionJson(),
                request.hookStep() != null && request.hookStep());
    }

    private Step toHookStep(Long hookId, SaveHookStepRequest request) {
        return toHookStep(null, hookId, request);
    }

    private Step toHookStep(Long id, Long hookId, SaveHookStepRequest request) {
        return new Step(
                id,
                hookId,
                request.code(),
                request.name(),
                request.type(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.enabled() == null || request.enabled(),
                request.configJson() == null ? "{}" : request.configJson(),
                request.extractionJson(),
                request.assertionJson(),
                true);
    }
}

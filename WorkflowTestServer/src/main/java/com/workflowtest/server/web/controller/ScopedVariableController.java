package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.ScopeType;
import com.workflowtest.server.service.definition.DefinitionModels.ScopedVariable;
import com.workflowtest.server.service.definition.ScopedVariableService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.SaveScopedVariableRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scoped-variables")
@RequiredArgsConstructor
public class ScopedVariableController {
    private final ScopedVariableService scopedVariableService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam ScopeType scopeType, @RequestParam Long scopeId) {
        return ApiResponse.ok(scopedVariableService.list(scopeType, scopeId));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveScopedVariableRequest request) {
        return ApiResponse.ok(scopedVariableService.save(toVariable(null, request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveScopedVariableRequest request) {
        return ApiResponse.ok(scopedVariableService.save(toVariable(id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scopedVariableService.delete(id);
        return ApiResponse.ok();
    }

    private ScopedVariable toVariable(Long id, SaveScopedVariableRequest request) {
        return new ScopedVariable(
                id,
                request.scopeType(),
                request.scopeId(),
                request.key(),
                request.valueType() == null ? "AUTO" : request.valueType(),
                request.value(),
                request.enabled() == null || request.enabled());
    }
}

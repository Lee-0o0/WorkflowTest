package com.workflowtest.server.web.controller;

import com.workflowtest.server.service.definition.DefinitionModels.GlobalVariable;
import com.workflowtest.server.service.definition.GlobalVariableService;
import com.workflowtest.server.web.ApiResponse;
import com.workflowtest.server.web.dto.RequestDtos.SaveGlobalVariableRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/global-variables")
@RequiredArgsConstructor
public class GlobalVariableController {
    private final GlobalVariableService globalVariableService;

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(globalVariableService.list());
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody SaveGlobalVariableRequest request) {
        return ApiResponse.ok(globalVariableService.save(toVariable(null, request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody SaveGlobalVariableRequest request) {
        return ApiResponse.ok(globalVariableService.save(toVariable(id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        globalVariableService.delete(id);
        return ApiResponse.ok();
    }

    private GlobalVariable toVariable(Long id, SaveGlobalVariableRequest request) {
        return new GlobalVariable(
                id,
                request.key(),
                request.valueType() == null ? "AUTO" : request.valueType(),
                request.value(),
                request.enabled() == null || request.enabled());
    }
}

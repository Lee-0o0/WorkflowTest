package com.workflowtest.server.api;

import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.application.VariableService;
import com.workflowtest.server.application.VariableService.Scope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VariableController {
    private final VariableService variables;
    public VariableController(VariableService variables) { this.variables = variables; }

    @GetMapping("/projects/{id}/variables") public List<VariableView> projectVariables(@PathVariable String id) { return variables.list(Scope.PROJECT, id); }
    @PostMapping("/projects/{id}/variables") public VariableView createProjectVariable(@PathVariable String id, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.PROJECT, id, null, request); }
    @PutMapping("/projects/{id}/variables/{variableId}") public VariableView updateProjectVariable(@PathVariable String id, @PathVariable String variableId, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.PROJECT, id, variableId, request); }
    @DeleteMapping("/projects/{id}/variables/{variableId}") public void deleteProjectVariable(@PathVariable String id, @PathVariable String variableId) { variables.delete(Scope.PROJECT, id, variableId); }

    @GetMapping("/groups/{id}/variables") public List<VariableView> groupVariables(@PathVariable String id) { return variables.list(Scope.GROUP, id); }
    @PostMapping("/groups/{id}/variables") public VariableView createGroupVariable(@PathVariable String id, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.GROUP, id, null, request); }
    @PutMapping("/groups/{id}/variables/{variableId}") public VariableView updateGroupVariable(@PathVariable String id, @PathVariable String variableId, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.GROUP, id, variableId, request); }
    @DeleteMapping("/groups/{id}/variables/{variableId}") public void deleteGroupVariable(@PathVariable String id, @PathVariable String variableId) { variables.delete(Scope.GROUP, id, variableId); }

    @GetMapping("/workflows/{id}/variables") public List<VariableView> workflowVariables(@PathVariable String id) { return variables.list(Scope.WORKFLOW, id); }
    @PostMapping("/workflows/{id}/variables") public VariableView createWorkflowVariable(@PathVariable String id, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.WORKFLOW, id, null, request); }
    @PutMapping("/workflows/{id}/variables/{variableId}") public VariableView updateWorkflowVariable(@PathVariable String id, @PathVariable String variableId, @Valid @RequestBody SaveVariableRequest request) { return variables.save(Scope.WORKFLOW, id, variableId, request); }
    @DeleteMapping("/workflows/{id}/variables/{variableId}") public void deleteWorkflowVariable(@PathVariable String id, @PathVariable String variableId) { variables.delete(Scope.WORKFLOW, id, variableId); }
}

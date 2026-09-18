package com.workflowtest.server.api;

import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.application.AssetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AssetController {
    private final AssetService assets;
    public AssetController(AssetService assets) { this.assets = assets; }

    @GetMapping("/projects/{projectId}/groups") public List<GroupView> groups(@PathVariable String projectId) { return assets.groups(projectId); }
    @PostMapping("/projects/{projectId}/groups") public GroupView createGroup(@PathVariable String projectId, @Valid @RequestBody SaveGroupRequest request) { return assets.saveGroup(projectId, null, request); }
    @PutMapping("/projects/{projectId}/groups/{id}") public GroupView updateGroup(@PathVariable String projectId, @PathVariable String id, @Valid @RequestBody SaveGroupRequest request) { return assets.saveGroup(projectId, id, request); }
    @DeleteMapping("/projects/{projectId}/groups/{id}") public void deleteGroup(@PathVariable String projectId, @PathVariable String id) { assets.deleteGroup(projectId, id); }

    @GetMapping("/groups/{groupId}/workflows") public List<WorkflowView> workflows(@PathVariable String groupId) { return assets.workflows(groupId); }
    @PostMapping("/groups/{groupId}/workflows") public WorkflowView createWorkflow(@PathVariable String groupId, @Valid @RequestBody SaveWorkflowRequest request) { return assets.saveWorkflow(groupId, null, request); }
    @PutMapping("/groups/{groupId}/workflows/{id}") public WorkflowView updateWorkflow(@PathVariable String groupId, @PathVariable String id, @Valid @RequestBody SaveWorkflowRequest request) { return assets.saveWorkflow(groupId, id, request); }
    @PutMapping("/workflows/{id}/draft") public WorkflowView updateDraft(@PathVariable String id, @Valid @RequestBody UpdateDraftRequest request) { return assets.updateDraft(id, request); }
    @DeleteMapping("/workflows/{id}") public void deleteWorkflow(@PathVariable String id) { assets.deleteWorkflow(id); }
    @PostMapping("/workflows/{id}/publish") public VersionView publish(@PathVariable String id) { return assets.publish(id); }
    @GetMapping("/workflows/{id}/versions") public List<VersionView> versions(@PathVariable String id) { return assets.versions(id); }
    @GetMapping("/workflow-versions/{id}/execution-package") public ExecutionPackage executionPackage(@PathVariable String id) { return assets.executionPackage(id); }
}

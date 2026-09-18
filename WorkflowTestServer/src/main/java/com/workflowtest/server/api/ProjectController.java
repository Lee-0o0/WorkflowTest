package com.workflowtest.server.api;

import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }
    @GetMapping public List<ProjectView> list() { return projects.list(); }
    @PostMapping public ProjectView create(@Valid @RequestBody SaveProjectRequest request) { return projects.create(request); }
    @PutMapping("/{id}") public ProjectView update(@PathVariable String id, @Valid @RequestBody SaveProjectRequest request) { return projects.update(id, request); }
    @GetMapping("/{id}/members") public List<MemberView> members(@PathVariable String id) { return projects.members(id); }
    @PutMapping("/{id}/members") public MemberView saveMember(@PathVariable String id, @Valid @RequestBody SaveMemberRequest request) { return projects.saveMember(id, request); }
    @DeleteMapping("/{id}/members/{userId}") public void removeMember(@PathVariable String id, @PathVariable String userId) { projects.removeMember(id, userId); }
    @DeleteMapping("/{id}") public void delete(@PathVariable String id) { projects.delete(id); }
}
